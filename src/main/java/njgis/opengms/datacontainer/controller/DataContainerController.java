package njgis.opengms.datacontainer.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sun.org.apache.regexp.internal.RE;
import lombok.extern.slf4j.Slf4j;
import njgis.opengms.datacontainer.bean.JsonResult;
import njgis.opengms.datacontainer.config.FtpConfig;
import njgis.opengms.datacontainer.config.FtpUtil;
import njgis.opengms.datacontainer.dao.*;
import njgis.opengms.datacontainer.entity.*;
import njgis.opengms.datacontainer.service.DataContainer;
import njgis.opengms.datacontainer.thread.UploadThread;
import njgis.opengms.datacontainer.utils.Utils;
import org.apache.commons.net.ftp.FTPClient;
import org.dom4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import sun.security.krb5.internal.PAData;

import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.ValidationEventLocator;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static njgis.opengms.datacontainer.config.FavFTPUtil.downloadFile;
import static njgis.opengms.datacontainer.config.FavFTPUtil.uploadFileFromProduction;

/**
 * @Author mingyuan
 * @Date 2020.06.11 15:46
 */
@RestController
@Slf4j
public class DataContainerController {
    @Autowired
    ImageDao imageDao;

    @Autowired
    DataContainer dataContainer;

    @Autowired
    DataListDao dataListDao;

    @Autowired
    DataListComDao dataListComDao;

    @Autowired
    BulkDataLinkDao bulkDataLinkDao;

    @Autowired
    VisualCategoryDao visualCategoryDao;

    @Autowired
    BulkDataLinkDao2 bulkDataLinkDao2;

    @Autowired
    DataListComDao2 dataListComDao2;

    @Autowired
    FtpConfig ftpConfig;

//    @Autowired
//    UserDao userDao;
//
//    @Autowired
//    TaskDao taskDao;

//    @Autowired
//    UploadThread uploadThread;

    @Value("${resourcePath}")
    private String resourcePath;

    @Value("${visualPath}")
    private String visualPath;

    //upload网页
    @RequestMapping("/testUpload")
    public ModelAndView testUpload() {
        ModelAndView testUpload = new ModelAndView();
        testUpload.setViewName("testUpload");
        return testUpload;
    }

    @RequestMapping("/operation")
    public ModelAndView operation(){
        ModelAndView operation = new ModelAndView();
        operation.setViewName("operation");
        return operation;
    }

    //断点续传工具接口
    @RequestMapping("/BPContinue")
    public ModelAndView BPContinue(){
        ModelAndView BPContinue = new ModelAndView();
        BPContinue.setViewName("BPContinue");
        return BPContinue;
    }

    //test接口
    @RequestMapping(value = "/uploadImg", method = RequestMethod.POST)
    public JsonResult  uploadImg(@RequestBody String img){
        Image image = new Image();
        String oid = UUID.randomUUID().toString();
        image.setOid(oid);
        String path = "/image/" + oid + ".jpg";
        Date now = new Date();
        image.setUpLoadTime(now);

        String[] strs = img.split(",");
        if (strs.length>1) {
            String imgStr = img.split(",")[1];
            Utils.base64StrToImage(imgStr, resourcePath + path);
            image.setPath(path);
        }else{
            image.setPath("");
        }
        imageDao.insert(image);
        JsonResult jsonResult = new JsonResult();
        jsonResult.setCode(1);
        return jsonResult;
    }

    //接口1改进 批量上传ogms数据并分开存储  含配置文件类
    @RequestMapping(value = "/configData", method = RequestMethod.POST)
    public JsonResult uploadData(@RequestParam("datafile")MultipartFile[] files,
                                 @RequestParam("name")String uploadName,
                                 @RequestParam("userId")String userName,
                                 @RequestParam("serverNode")String serverNode,
                                 @RequestParam("origination")String origination) throws IOException, DocumentException {
        String apiType = "configData";
        JsonResult jsonResult = new JsonResult();
        Date start = new Date();
        log.info("start time is " + start);
        boolean loadFileLog = false;
        boolean configExist = true;
        Date now = new Date();
        BulkDataLink bulkDataLink = new BulkDataLink();
        String uuid = UUID.randomUUID().toString();
        String dataTemplateId = "";
        String dataTemplate = "";
        String DataTemplateType = "";
        //参数检验
        if (uploadName.trim().equals("")||userName.trim().equals("")||serverNode.trim().equals("")||origination.trim().equals("")){
            jsonResult.setCode(-1);
            jsonResult.setResult("err");
            jsonResult.setMessage("without name or userId or origination or serverNode");
            return jsonResult;
        }

        String ogmsPath;
        ogmsPath = resourcePath + "/" + uuid;
        //文件检验
        if (files.length==0){
            loadFileLog = false;
        }else if (files.length == 1){
            //只有一个配置文件
            if (files[0].getOriginalFilename().equals("config.udxcfg")){
                loadFileLog = false;
                jsonResult.setMessage("Only config file,no others");
                jsonResult.setCode(-1);
                jsonResult.setResult("err");
                return jsonResult;
            }else {
                //无配置文件
                loadFileLog = false;
                jsonResult.setCode(-1);
                jsonResult.setMessage("No config file");
                jsonResult.setResult("err");
                return jsonResult;
            }
        }else {
            boolean config = false;
            for (MultipartFile file:files){
                //有多个文件，且含有配置文件
                if (Objects.equals(file.getOriginalFilename(), "config.udxcfg")){
                    config = true;
                    //检查配置文件格式 ,通过String转xml，逐行读取配置文件内容
                    Reader reader = null;
                    reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
                    BufferedReader br = new BufferedReader(reader);
                    String line;
                    StringBuilder content = new StringBuilder();
                    while((line = br.readLine())!=null){
                        content.append(line);
                    }
                    //去除string中的空格\t、回车\n、换行符\r、制表符\t
                    String dest = new String(content);
                    dest = dest.replaceAll("\t","");
                    content = new StringBuilder(dest);

                    //用正则表达式匹配content是否包含xml非法字符  ' " > < &
                    String pattern = ".*&.*";
                    boolean isMatch = Pattern.matches(pattern, content.toString());
                    //如果含有非法字符，则用CDATA包裹
                    if (isMatch){
                        //匹配头
                        String pattern1 = "<add";
                        Pattern p1 = Pattern.compile(pattern1);
                        Matcher m1 = p1.matcher(content.toString());
                        content = new StringBuilder(m1.replaceAll("<![CDATA[<add"));
                        //匹配尾
                        String pattern2 = "/>";
                        Pattern p2 = Pattern.compile(pattern2);
                        Matcher m2 = p2.matcher(content.toString());
                        content = new StringBuilder(m2.replaceAll("/>]]>"));
                    }
                    Document configXML = DocumentHelper.parseText(content.toString());
                    //获取根元素
                    Element root = configXML.getRootElement();
                    //判断DataTemplate里包含的是id还是schema
                    DataTemplateType = root.element("DataTemplate").attribute("type").getText();
                    if (DataTemplateType.equals("id")){
                        dataTemplateId = root.element("DataTemplate").getText();
                    }else if (DataTemplateType.equals("schema")){
                        //利用正则表达式截取DataTemplate下的schema数据
                        String xml = new String(content);
                        String tag = "UdxDeclaration";
                        String rgex = "<"+tag+">(.*?)</"+tag+">";
                        Pattern p = Pattern.compile(rgex);
                        Matcher m = p.matcher(xml);
                        String context = "";
                        List<String> list = new ArrayList<String>();
                        while (m.find()) {
                            int i = 1;
                            list.add(m.group(i));
                            i++;
                        }
                        //只要匹配的第一个
                        if(list.size()>0){
                            context = list.get(0);
                        }
                        log.info(context);
                        context = "<UdxDeclaration>" + context + "</UdxDeclaration>";
                        log.info(context);
                        dataTemplate = context;
                        log.info(dataTemplate);
                    }
                    //首先判断文件个数,一个文件也压缩上传
                    loadFileLog = dataContainer.uploadOGMSMulti(bulkDataLink,ogmsPath,uuid,files,configExist,apiType);
                    break;
                }
            }
            if (!config){
                //有多个文件，但不含有配置文件
                jsonResult.setCode(-1);
                jsonResult.setResult("err");
                jsonResult.setMessage("No config file");
                return jsonResult;
            }
        }
        if (loadFileLog){
            //信息入库
            bulkDataLink.setDate(now);
            bulkDataLink.setName(uploadName);
            bulkDataLink.setOrigination(origination);
            bulkDataLink.setServerNode(serverNode);
            bulkDataLink.setUid(userName);
            bulkDataLink.setZipOid(uuid);
            bulkDataLink.setPath(ogmsPath);
            bulkDataLink.setConfigFile(true);
            if (DataTemplateType.equals("id")){
                bulkDataLink.setDataTemplateId(dataTemplateId);
                bulkDataLink.setType("template");
            }else if (DataTemplateType.equals("schema")){
                bulkDataLink.setDataTemplate(dataTemplate);
                bulkDataLink.setType("schema");
            }else {
                bulkDataLink.setType(DataTemplateType);
            }
            bulkDataLinkDao.save(bulkDataLink);

            jsonResult.setCode(1);
            jsonResult.setMessage("upload file success!");
            jsonResult.setResult("suc");
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("source_store_id",uuid);
            jsonObject.put("file_name",uploadName);
            jsonResult.setData(jsonObject);
        }
        Date end = new Date();
        log.info("end time is " + end);
        return jsonResult;
    }

    /**
     * 下载数据
     * @param uid 数据uid
     * @param type 是下载还是展示，如果为null直接下载，如果为html，则header设置为html
     * @param response response
     * @throws UnsupportedEncodingException 异常处理
     */
    @RequestMapping(value = "/data/{uid}", method = RequestMethod.GET)
    public void downLoadFile(@PathVariable String uid, @RequestParam(value = "type", required = false)String type,
                             HttpServletResponse response) throws UnsupportedEncodingException {
        boolean downLoadLog = false;
        String oid = uid;
        downLoadLog = dataContainer.downLoad(oid,response, type);
    }

//    /**
//     * 展示html，而不是下载
//     * @param uid 数据下载id
//     * @param response response
//     */
//    @RequestMapping(value = "/data/{uid}", method = RequestMethod.GET)
//    public void showHTMLFile(@PathVariable String uid, @RequestParam(value = "type") String type, HttpServletResponse response){
//        log.info(uid);
//        log.info(type);
//    }

    //接口3 删除指定上传数据
    @RequestMapping(value = "/data/{uid}", method = RequestMethod.DELETE)
    public JsonResult del(@PathVariable String uid){
        JsonResult jsonResult = new JsonResult();
        String oid = uid;
//        boolean delLog = false;

        BulkDataLink bulkDataLink = bulkDataLinkDao.findFirstByZipOid(oid);
        jsonResult = dataContainer.delete(oid, jsonResult);
//        if (jsonResult.getCode() == 0){
//        }else {
//
//            jsonResult.setCode(-1);
//            jsonResult.setData("");
//            jsonResult.setMessage("delete file fail");
//        }
        return jsonResult;
    }

    //接口4 批量下载
    @RequestMapping(value = "/batchData",method = RequestMethod.GET)
    public void bulkDownLoad(@RequestParam(value = "oids") List<String> oids, HttpServletResponse response)
            throws UnsupportedEncodingException {
        boolean downLoadLog = false;
        boolean delCacheFile = false;
        File[] files = new File[oids.size()];
        for (int i=0;i<oids.size();i++){
//            DataList dataList = dataListDao.findFirstByUid(oids.get(i));
            BulkDataLink bulkDataLink = bulkDataLinkDao.findFirstByZipOid(oids.get(i));
            if (bulkDataLink == null){
                //两种情况，一种情况是oid输入错误，另一种情况是输入的是dataListCom的oid
                DataListCom dataListCom = dataListComDao.findFirstByOid(oids.get(i));
                if (dataListCom == null){
                    downLoadLog = false;
                    return;
                }else {
                    //下载单文件
                    String fileSingle = dataListCom.getPath() + "/"+dataListCom.getFileName();
                    File file = new File(fileSingle);
                    files[i] = file;
                }
            }else {
                String downLoadPath = bulkDataLink.getPath();
                if (bulkDataLink.getDataOids().size() == 1) {
                    DataListCom dataListCom = dataListComDao.findFirstByOid(bulkDataLink.getDataOids().get(0));
                    String fileSingle = dataListCom.getPath() + "/" + dataListCom.getFileName();
                    File file = new File(fileSingle);
                    files[i] = file;
                } else {
                    String fileZip = bulkDataLink.getZipOid() + ".zip";
                    File file = new File(downLoadPath + "/" + fileZip);
                    files[i] = file;
                }
            }
        }
        JsonResult jsonResult = new JsonResult();
        jsonResult = dataContainer.downLoadBulkFile(response,files);
        if (jsonResult.getCode() == 0){
            //如果下载成功，则将打包存储在服务器的文件删除
            delCacheFile = dataContainer.deleteFolder(jsonResult.getData().toString());
            if (!delCacheFile){
                jsonResult.setCode(0);
                jsonResult.setResult("err");
                jsonResult.setMessage("downLoad success but delete cache file fail");
            }else {
                jsonResult.setCode(1);
                jsonResult.setResult("suc");
                jsonResult.setMessage("downLoad success");
            }
        }else {
            jsonResult.setMessage("downLoad failed");
            jsonResult.setResult("err");
            jsonResult.setCode(-1);
        }
        return;
    }

    //接口5 批量删除
    @RequestMapping(value = "/batchData",method = RequestMethod.DELETE)
    public JsonResult bulkDel(@RequestParam(value = "oids") List<String> oids){
        JsonResult jsonResult = new JsonResult();
        boolean delLog = false;
        for (int i=0;i<oids.size();i++) {
            jsonResult = dataContainer.delete(oids.get(i), jsonResult);
            if (jsonResult.getCode() == -1) {
                BulkDataLink bulkDataLink = bulkDataLinkDao.findFirstByZipOid(oids.get(i));
                String failName = bulkDataLink.getName();
                jsonResult.setCode(-1);
                jsonResult.setResult("err");
                jsonResult.setMessage(failName + "delete fail," + jsonResult.getMessage());
                return jsonResult;
            }
        }
        if (jsonResult.getCode() == 1){
            jsonResult.setCode(1);
            jsonResult.setResult("suc");
            jsonResult.setMessage("All file delete success");
        }
        return jsonResult;
    }

    //接口6 无需配置文件上传接口,兼容数据交换
    @RequestMapping(value = "/data", method = RequestMethod.POST)
    public JsonResult dataNoneConfig(@RequestParam(value = "datafile", required = false)MultipartFile[] files,
                                 @RequestParam(value = "name", required = false)String uploadName,
                                 @RequestParam(value = "userId", required = false)String userName,
                                 @RequestParam(value = "serverNode", required = false)String serverNode,
                                 @RequestParam(value = "origination", required = false)String origination,
                                     @RequestParam(value = "datatag", required = false) String datatag) throws IOException {
        JsonResult jsonResult = new JsonResult();
        String apiType = "data";
        boolean loadFileLog = false;
        boolean configExist = false;
        Date now = new Date();
        BulkDataLink bulkDataLink = new BulkDataLink();
        String uuid = UUID.randomUUID().toString();
        //参数检验 不需要检验了，兼容没有的
//        if (uploadName.trim().equals("")||userName.trim().equals("")||serverNode.trim().equals("")||origination.trim().equals("")){
//            jsonResult.setCode(-1);
//            jsonResult.setMessage("without name or userId or origination or serverNode");
//            return jsonResult;
//        }
        String ogmsPath;
        ogmsPath = resourcePath + "/" + uuid;
        //文件检验
        if (files.length==0){
            loadFileLog = false;
        }else{
            loadFileLog = dataContainer.uploadOGMSMulti(bulkDataLink,ogmsPath,uuid,files,configExist, apiType);
        }
        if (loadFileLog){
            //信息入库
            bulkDataLink.setDate(now);
            bulkDataLink.setName(uploadName);
            bulkDataLink.setOrigination(origination);
            bulkDataLink.setServerNode(serverNode);
            bulkDataLink.setUid(userName);
            bulkDataLink.setZipOid(uuid);
            bulkDataLink.setPath(ogmsPath);
            bulkDataLink.setConfigFile(false);
            bulkDataLinkDao.save(bulkDataLink);

            jsonResult.setCode(1);
            jsonResult.setResult("suc");
            jsonResult.setMessage("upload file success!");
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("id",uuid);
            jsonObject.put("file_name",uploadName);
            jsonResult.setData(jsonObject);
        }
        return jsonResult;
    }

    //可视化接口
    @RequestMapping(value = "/data/{uid}/preview", method = RequestMethod.GET)
    public void visual(@PathVariable(value = "uid") String uid,HttpServletResponse response,
                       @RequestParam(value = "type", required = false) String type) throws Exception {
        String oid = uid;
        File picCache = new File(visualPath + "/" + oid + ".png");
        if (!picCache.exists()) {
            BulkDataLink bulkDataLink = bulkDataLinkDao.findFirstByZipOid(oid);
            String dataTemplateId = bulkDataLink.getDataTemplateId();
            VisualCategory visualCategory = visualCategoryDao.findFirstByOid(dataTemplateId);
            String visualType = visualCategory.getCategory();
            //获取可视化文件的path
            String path = null;
            //将zip包进行解压
            String zipPath = bulkDataLink.getPath();
            String zipFile = bulkDataLink.getPath() + "/" + bulkDataLink.getZipOid() + ".zip";
            dataContainer.zipUncompress(zipFile, zipPath);
            log.info("已解压文件，过");
            //匹配shp或tiff文件
            for (String dataOid : bulkDataLink.getDataOids()) {
                DataListCom dataListCom = dataListComDao.findFirstByOid(dataOid);
                String fileName = dataListCom.getFileName();
                //取文件名后缀
                String suffix = fileName.substring(fileName.lastIndexOf(".") + 1);
                if (suffix.equals("shp")) {
                    path = zipPath;
                } else if (suffix.equals("tif")) {
                    path = zipPath + "/" + dataListCom.getFileName();
                }
            }
            log.info("已取得后缀，确定可视化类型，过");

//        String picId = UUID.randomUUID().toString();
//            String outPath = "E:\\upload\\picCache" + "\\" + oid;//dev
            String outPath = "/data/picCache" + "/" + oid;//prod
            if (visualType.equals("shp")) {
                //调用shp可视化方法
                try {
//                    String[] args = new String[]{"python", "E:\\upload\\upload_ogms\\shpSnapshot.py", String.valueOf(path), outPath};//dev
                    String[] args = new String[]{"python", "/data/visualMethods/shpSnapshot.py", String.valueOf(path), String.valueOf(outPath)};//prod
                    log.info("input: " + path + "output: " + outPath);

                    Process proc = Runtime.getRuntime().exec(args);// 执行py文件

                    BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                    String line = null;
                    while ((line = in.readLine()) != null) {
                        System.out.println(line);
                    }
                    in.close();
                    proc.waitFor();
                    log.info("成功执行shp处理文件，pass");
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else if (visualType.equals("tiff")) {
                //调用tiff可视化方法
                try {
//                    String[] args = new String[]{"python", "E:\\upload\\upload_ogms\\tiff.py", String.valueOf(path), String.valueOf(oid)};//dev
                    String[] args = new String[]{"python", "/data/visualMethods/tiff.py", String.valueOf(path), String.valueOf(oid)};//prod

                    Process proc = Runtime.getRuntime().exec(args);// 执行py文件
                    log.info("成功执行tiff处理文件，pass");

                    BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                    String line = null;
                    while ((line = in.readLine()) != null) {
                        System.out.println(line);
                    }
                    in.close();
                    proc.waitFor();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            //执行python脚本之后删除解压后的文件
            dataContainer.deleteZipUncompress(zipFile, zipPath);
            log.info("删除解压后的文件成功，pass");
            File picFile = new File(visualPath + "/" + oid + ".png");
            dataContainer.downLoadFile(response, picFile, oid + ".png",type);
            log.info("首次下载成功");
        }else {
            //将生成的文件进行下载
            File picFile = new File(visualPath + "/" + oid + ".png");
            dataContainer.downLoadFile(response, picFile, oid + ".png", type);
            log.info("取得缓存下载成功");
        }
    }

    //增加可视化方法
    @RequestMapping(value = "/addVisual", method = RequestMethod.POST)
    public JsonResult addVisual(@RequestParam(value = "oid") String oid,
                                @RequestParam(value = "category") String category) {
        JsonResult jsonResult = new JsonResult();
        VisualCategory visualCategory = new VisualCategory();
        visualCategory.setCategory(category);
        visualCategory.setOid(oid);
        visualCategoryDao.save(visualCategory);
        jsonResult.setResult("suc");
        jsonResult.setMessage("add visual method success!");
        jsonResult.setCode(1);
        return jsonResult;
    }

    //断点续传接口Breakpoint continuation
    @RequestMapping(value = "/dataBPContinue", method = RequestMethod.GET)
    public void dataBPContinue(@RequestParam(value = "oid") String oid,
                               @RequestParam(value = "savePath") String savePath,
                               HttpServletResponse response) throws IOException, InterruptedException {
        //savePath为存储路径，用于存储临时文件等文件
        boolean downLoadLog = false;
        downLoadLog = dataContainer.downBPContinue(oid,savePath,response);
    }

    //以本名上传

    //新增dataTemplateId接口
    @RequestMapping(value = "/editTemplateId",method = RequestMethod.POST)
    public JsonResult addTemplateId(@RequestParam(value = "oid") String oid,
                                    @RequestParam(value = "templateId") String templateId,
                                    @RequestParam(value = "type") String type){
        JsonResult result = new JsonResult();
        BulkDataLink bulkDataLink = bulkDataLinkDao.findFirstByZipOid(oid);

        //编辑templateId
        if (type.equals("edit")){
            if (bulkDataLink.getDataTemplateId() == null){
                result.setMessage("dataTemplateId not exist!!!");
                result.setResult("err");
                result.setCode(-1);
                return result;
            }else {
                bulkDataLink.setDataTemplateId(templateId);
                bulkDataLinkDao.save(bulkDataLink);
                result.setMessage("edit success");
                result.setCode(1);
                result.setData("oid is "+ oid);
                return result;
            }
        }else {
            //判断oid的configFile是否为false
            if (bulkDataLink.getConfigFile()) {
                result.setCode(-1);
                result.setResult("err");
                result.setMessage("Only data without template id can be added");
                return result;
            } else {
                //新增templateId
                bulkDataLink.setDataTemplateId(templateId);
                bulkDataLinkDao.save(bulkDataLink);
                result.setCode(1);
                result.setMessage("add success");
                result.setData("oid is " + oid);
            }
            return result;
        }
    }

    //全局搜索功能
    @RequestMapping(value = "/globalSearch", method = RequestMethod.GET)
    public JsonResult globalSearch(@RequestParam(value = "name") String name){
        JsonResult result = new JsonResult();
        HashMap<String,String> data = new HashMap<>();
        List<BulkDataLink> bulkDataLinks = new ArrayList<>();
        ArrayList<HashMap> datas = new ArrayList<>();

        bulkDataLinks = bulkDataLinkDao.findAll();

        for (BulkDataLink bulkDataLink:bulkDataLinks){
            if (bulkDataLink.getName().equals(name)){
                data.put("name",name);
                data.put("oid",bulkDataLink.getZipOid());
                datas.add(data);
            }
        }
        result.setData(datas);
        result.setCode(0);
        return result;
    }

    //获取元数据接口
    @RequestMapping(value = "/data/{uid}/metadata", method = RequestMethod.GET)
    public JsonResult getMetaData(@PathVariable(value = "uid") String dataId){
        JsonResult jsonResult = new JsonResult();
        BulkDataLink bulkDataLink = bulkDataLinkDao.findFirstByZipOid(dataId);
        List<DataListCom> dataListComs = new ArrayList<>();
        if (bulkDataLink==null){
            jsonResult.setCode(-1);
            jsonResult.setResult("err");
            jsonResult.setMessage("The data was not found!");
            return jsonResult;
        }else {
            for (String dataOid: bulkDataLink.getDataOids()){
                dataListComs.add(dataListComDao.findFirstByOid(dataOid));
            }
            bulkDataLink.setDataListComs(dataListComs);
            jsonResult.setCode(1);
            jsonResult.setMessage("success");
            jsonResult.setResult("suc");
            jsonResult.setData(bulkDataLink);
            return jsonResult;
        }
    }

    //大文件上传至ftp服务器
    @RequestMapping(value = "/uploadBigFile", method = RequestMethod.POST)
    public JsonResult uploadBigFile(@RequestParam(value = "bigFile") MultipartFile file) throws IOException {
        JsonResult result = new JsonResult();
        String hostname = "192.168.47.130";
        int port = 21;
        String username = "ogms1";
        String password = "123456";

        boolean res = uploadFileFromProduction(hostname, port, username, password, "/ogms", file);

        return result;
    }


    //大文件下载
    @RequestMapping(value = "/downloadBigFile", method = RequestMethod.GET)
    public JsonResult downloadBigFile(@RequestParam(value = "fileName") String fileName,
                                      @RequestParam(value = "type", required = false) String type,
                                      HttpServletResponse response) throws UnsupportedEncodingException {
        JsonResult result = new JsonResult();
        String hostname = "192.168.47.130";
        int port = 21;
        String username = "ogms1";
        String password = "123456";

        String pathName = "/ogms";
        String filePath = "/data/ftp/ogms";
//        String filePath = "C:\\Users\\HP\\Desktop\\";
        String localpath = "/data/ftp";

        File file = new File(filePath+"/" + fileName);
        if (!file.exists()){
//            result.setData(-1);
            result.setMessage("file no exit!");
            result.setCode(-1);
            result.setResult("err");
            return result;
        }
        dataContainer.downLoadFile(response, file, fileName,type);


        downloadFile(hostname, port, username, password, pathName, fileName, localpath);

        return result;
    }

//    @RequestMapping(value = "/getPortal", method = RequestMethod.GET)
//    public JsonResult getPortal(){
//        JsonResult jsonResult = new JsonResult();
//
//        //拿到门户用户数据id
//        List<String> urls = new ArrayList<>();
//        List<User> users = userDao.findAll();
//        for (User user:users){
//            List<FileMeta> fileMetas = user.getFileContainer();
//            if (fileMetas == null){
//                continue;
//            }else {
//                for(FileMeta fileMeta:fileMetas){
//                    if (fileMeta.getUrl().equals("")||fileMeta.getUrl() == null){
//                        continue;
//                    }else {
////                        String oid = fileMeta.getUrl().split("=")[1];
//                        urls.add(fileMeta.getUrl());
//                    }
//                }
//            }
//        }
//
//        jsonResult.setData(urls);
//        return jsonResult;
//    }
//
//    @RequestMapping(value = "/getTask", method = RequestMethod.GET)
//    public JsonResult getTask(){
//        JsonResult jsonResult = new JsonResult();
//
//        //拿到门户用户数据id
//        List<String> urls = new ArrayList<>();
//        List<Task> tasks = taskDao.findAll();
//        HashSet<String> userId = new HashSet<>();
////        for (Task task : tasks){
////            if(task.getStatus() == 2){
////                userId.add(task.getUserId());
////            }
////        }
//        for (Task task : tasks){
//            if(task.getStatus() == 2&&(task.getUserId().equals("wxsh@cugb.edu.cn")||task.getUserId().equals("641597542@qq.com")
//                    ||task.getUserId().equals("786872808@qq.com")||task.getUserId().equals("chenmin")
//                    ||task.getUserId().equals("wutingxin1998@gmail.com")||task.getUserId().equals("1061401953@qq.com")
//                    ||task.getUserId().equals("Xun Shi")||task.getUserId().equals("1245181489@qq.com")
//                    ||task.getUserId().equals("hqsong@henu.edu.cn")||task.getUserId().equals("xinyue.ye@njit.edu")||
//                    task.getUserId().equals("yuesongshan")||task.getUserId().equals("zhengzhong2020@qq.com")
//                    ||task.getUserId().equals("wxc627684875")||task.getUserId().equals("chenmin0902@163.com")
//                    ||task.getUserId().equals("songc@lreis.ac.cn")||task.getUserId().equals("473701638@qq.com")
//                    ||task.getUserId().equals("307338820@qq.com")||task.getUserId().equals("1643696225@qq.com")
//                    ||task.getUserId().equals("lihongyi@lzb.ac.cn")||task.getUserId().equals("Wtian@lzu.edu.cn"))){
//                List<TaskData> inputs = task.getInputs();
//                for (TaskData taskData : inputs){
//                    urls.add(taskData.getUrl());
//                }
//            }else {
//                continue;
//            }
//        }
//
//        jsonResult.setData(urls);
//        return jsonResult;
//    }

    /**
     * 将数据库字段有用的进行迁移
     * @param oid 待迁移的BulkDataLink2的id
     * @return 迁移结果
     */
    @RequestMapping(value = "/insertData/{oid}", method = RequestMethod.GET)
    public JsonResult insertData(@PathVariable(value = "oid") String oid){
        JsonResult jsonResult = new JsonResult();
        BulkDataLink2 bulkDataLink2 = bulkDataLinkDao2.findFirstByZipOid(oid);
        List<String> dataOids = bulkDataLink2.getDataOids();

        bulkDataLinkDao.insert(bulkDataLink2);
        for (String dataOid:dataOids){
            DataListCom2 dataListCom2 = dataListComDao2.findFirstByOid(dataOid);
            dataListCom2.setPath("/data/dataSource/upload_dataContainer/" + dataOid);
            dataListComDao.insert(dataListCom2);
        }

        return jsonResult;
    }

//    /**
//     *
//     */
    @RequestMapping(value = "/findComNameNone", method = RequestMethod.GET)
    public ArrayList<String> findComNameNone(){
        List<DataListCom> dataListComs = dataListComDao.findAll();
        ArrayList<String> list = new ArrayList<>();
        for (DataListCom dataListCom:dataListComs){
            if(dataListCom.getFileName().equals("")){
                list.add(dataListCom.getOid());
                dataListComDao.delete(dataListCom);
            }
        }
        return list;
    }


    /**
     * 75容器中转接口，将75页面的数据存储到本服务器本地，将数据路径返回
     * @param filesUrl 文件url
     * @return 数据路径
     */
    @RequestMapping(value = "/dataDownloadContainer", method = RequestMethod.POST)
    public JsonResult dataDownloadContainer(@RequestParam(value = "datafileUrl", required = false)String[] filesUrl) throws IOException {
        JsonResult jsonResult = new JsonResult();
        if(filesUrl.length < 1){
            jsonResult.setMessage("url is none");
            jsonResult.setCode(-1);
            return jsonResult;
        }
        List<String> paths = new ArrayList<>();
        String uuid = UUID.randomUUID().toString();
        String dirPath = resourcePath + "/" + uuid;
        for(int i=0;i<filesUrl.length;i++){
            boolean isDownload = dataContainer.downloadContainer(filesUrl[i],dirPath);
            if(!isDownload){
                jsonResult.setCode(-1);
                jsonResult.setMessage("download file failed");
                return jsonResult;
            }
        }

        jsonResult.setData(dirPath);
        jsonResult.setCode(0);
        return jsonResult;
    }


    @RequestMapping(value = "/dataDownloadAndCpmpress", method = RequestMethod.POST)
    public JsonResult dataDownloadAndCpmpress(@RequestParam(value = "resources", required = false)MultipartFile[] files) throws Exception {
        JsonResult jsonResult = new JsonResult();
        String uuid = UUID.randomUUID().toString();
        String destDirPath = resourcePath + "/" + uuid;
        File testData = new File(destDirPath);
        if(!testData.exists()){
            testData.mkdirs();
        }
        String dirFilePath = destDirPath + "/res.zip";

        File localFile = new File(dirFilePath);
        FileOutputStream fos = null;
        InputStream in = null;
        try {
            if (localFile.exists()) {
                //如果文件存在删除文件
                boolean delete = localFile.delete();
            }
            //创建文件
            if (!localFile.exists()) {
                //如果文件不存在，则创建新的文件
                localFile.createNewFile();
            }

            //创建文件成功后，写入内容到文件里
            fos = new FileOutputStream(localFile);
            in = files[0].getInputStream();

            byte[] bytes = new byte[1024];
            int len = -1;
            while ((len = in.read(bytes)) != -1) {
                fos.write(bytes, 0, len);
            }
            fos.flush();
            log.info("Reading uploaded file and buffering to local successfully!");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
                if (in != null) {
                    in.close();
                }
            }catch (IOException e) {
                log.error("InputStream or OutputStream close error : {}", e);
            }
        }

        //解压
        dataContainer.zipUncompress(dirFilePath, destDirPath);
        jsonResult.setData(destDirPath);
        jsonResult.setCode(0);
        jsonResult.setMessage("suc");
        return jsonResult;
    }

    /**
     * 75容器中转接口，将绑定好的数据删除
     * @param filesUrl
     * @return
     */
    @RequestMapping(value = "/dataDelete", method = RequestMethod.POST)
    public JsonResult dataDelete(@RequestParam(value = "datafileUrl", required = false)String[] filesUrl) {
        JsonResult jsonResult = new JsonResult();


        return jsonResult;
    }

}