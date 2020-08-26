package njgis.opengms.datacontainer.controller;

import com.alibaba.fastjson.JSONObject;
import com.sun.org.apache.regexp.internal.RE;
import lombok.extern.slf4j.Slf4j;
import njgis.opengms.datacontainer.bean.JsonResult;
import njgis.opengms.datacontainer.dao.*;
import njgis.opengms.datacontainer.entity.BulkDataLink;
import njgis.opengms.datacontainer.entity.DataListCom;
import njgis.opengms.datacontainer.entity.Image;
import njgis.opengms.datacontainer.entity.VisualCategory;
import njgis.opengms.datacontainer.service.DataContainer;
import njgis.opengms.datacontainer.utils.Utils;
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
    @RequestMapping(value = "/data", method = RequestMethod.POST)
    public JsonResult uploadData(@RequestParam("ogmsdata")MultipartFile[] files,
                                 @RequestParam("name")String uploadName,
                                 @RequestParam("userId")String userName,
                                 @RequestParam("serverNode")String serverNode,
                                 @RequestParam("origination")String origination) throws IOException, DocumentException {
        JsonResult jsonResult = new JsonResult();
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
            jsonResult.setMsg("without name or userId or origination or serverNode");
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
                jsonResult.setMsg("Only config file,no others");
                jsonResult.setCode(-1);
                return jsonResult;
            }else {
                //无配置文件
                loadFileLog = false;
                jsonResult.setCode(-1);
                jsonResult.setMsg("No config file");
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
                    loadFileLog = dataContainer.uploadOGMSMulti(bulkDataLink,ogmsPath,uuid,files,configExist);
                    break;
                }
            }
            if (!config){
                //有多个文件，但不含有配置文件
                jsonResult.setCode(-1);
                jsonResult.setMsg("No config file");
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

            jsonResult.setCode(0);
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("source_store_id",uuid);
            jsonObject.put("file_name",uploadName);
            jsonResult.setData(jsonObject);
        }
        return jsonResult;
    }

    //接口2 下载数据
    @RequestMapping(value = "/data", method = RequestMethod.GET)
    public void downLoadFile(@RequestParam String uid, HttpServletResponse response) throws UnsupportedEncodingException {
        boolean downLoadLog = false;
        String oid = uid;
        downLoadLog = dataContainer.downLoad(oid,response);
        JsonResult jsonResult = new JsonResult();
        if (downLoadLog){
            jsonResult.setMsg("download success");
            jsonResult.setCode(0);
        }else {
            jsonResult.setMsg("download fail");
            jsonResult.setCode(-1);
        }
//        return jsonResult;
    }

    //接口3 删除指定上传数据
    @RequestMapping(value = "/del", method = RequestMethod.DELETE)
    public JsonResult del(@RequestParam String uid){
        JsonResult jsonResult = new JsonResult();
        String oid = uid;
        boolean delLog = false;

        BulkDataLink bulkDataLink = bulkDataLinkDao.findFirstByZipOid(oid);
        delLog = dataContainer.delete(oid);
        if (delLog){
            jsonResult.setMsg("delete success");
            jsonResult.setCode(0);
        }else {
            jsonResult.setCode(-1);
            jsonResult.setMsg("delete fail");
        }
        return jsonResult;
    }

    //接口4 批量下载
    @RequestMapping(value = "/bulkDownload",method = RequestMethod.GET)
    public void bulkDownLoad(@RequestParam(value = "oids") List<String> oids, HttpServletResponse response) throws UnsupportedEncodingException {
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
                jsonResult.setMsg("downLoad success but delete cache file fail");
            }else {
                jsonResult.setCode(0);
                jsonResult.setMsg("downLoad success");
            }
        }else {
            jsonResult.setMsg("downLoad failed");
            jsonResult.setCode(-1);
        }
        return;
    }

    //接口5 批量删除
    @RequestMapping(value = "bulkDel",method = RequestMethod.DELETE)
    public JsonResult bulkDel(@RequestParam(value = "oids") List<String> oids){
        JsonResult jsonResult = new JsonResult();
        boolean delLog = false;
        for (int i=0;i<oids.size();i++) {
            delLog = dataContainer.delete(oids.get(i));
            if (!delLog) {
                BulkDataLink bulkDataLink = bulkDataLinkDao.findFirstByZipOid(oids.get(i));
                String failName = bulkDataLink.getName();
                jsonResult.setCode(-1);
                jsonResult.setMsg(failName + "delete fail");
                return jsonResult;
            }
        }
        if (delLog){
            jsonResult.setCode(0);
            jsonResult.setMsg("All fail delete success");
        }
        return jsonResult;
    }

    //接口6 无需配置文件上传接口
    @RequestMapping(value = "/dataNoneConfig", method = RequestMethod.POST)
    public JsonResult dataNoneConfig(@RequestParam("ogmsdata")MultipartFile[] files,
                                 @RequestParam("name")String uploadName,
                                 @RequestParam("userId")String userName,
                                 @RequestParam("serverNode")String serverNode,
                                 @RequestParam("origination")String origination) throws IOException {
        JsonResult jsonResult = new JsonResult();
        boolean loadFileLog = false;
        boolean configExist = false;
        Date now = new Date();
        BulkDataLink bulkDataLink = new BulkDataLink();
        String uuid = UUID.randomUUID().toString();
        //参数检验
        if (uploadName.trim().equals("")||userName.trim().equals("")||serverNode.trim().equals("")||origination.trim().equals("")){
            jsonResult.setCode(-1);
            jsonResult.setMsg("without name or userId or origination or serverNode");
            return jsonResult;
        }
        String ogmsPath;
        ogmsPath = resourcePath + "/" + uuid;
        //文件检验
        if (files.length==0){
            loadFileLog = false;
        }else{
            loadFileLog = dataContainer.uploadOGMSMulti(bulkDataLink,ogmsPath,uuid,files,configExist);
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

            jsonResult.setCode(0);
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("source_store_id",uuid);
            jsonObject.put("file_name",uploadName);
            jsonResult.setData(jsonObject);
        }
        return jsonResult;
    }

    //可视化接口
    @RequestMapping(value = "/visual", method = RequestMethod.GET)
    public void visual(@RequestParam(value = "uid") String uid,HttpServletResponse response) throws Exception {
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
//                    String[] args = new String[]{"python", "E:\\upload\\upload_ogms\\shpSnapshot.py", String.valueOf(path), String.valueOf(outPath)};//dev
                    String[] args = new String[]{"python", "/data/visualMethods/shpSnapshot.py", String.valueOf(path), String.valueOf(outPath)};//prod
                    log.info("input: " + path + "output: " + outPath);;

                    //部署时解开
//                String[] args = new String[] { "python", "/data/dataSource/upload_ogms/shp.py", String.valueOf(path), String.valueOf(picId) };
                    Process proc = Runtime.getRuntime().exec(args);// 执行py文件
                    log.info("成功执行shp处理文件，pass");

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
            } else if (visualType.equals("tiff")) {
                //调用tiff可视化方法
                try {
//                    String[] args = new String[]{"python", "E:\\upload\\upload_ogms\\tiff.py", String.valueOf(path), String.valueOf(oid)};//dev
                    String[] args = new String[]{"python", "/data/visualMethods/tiff.py", String.valueOf(path), String.valueOf(oid)};//prod
                    //部署时解开
//                String[] args = new String[] { "python", "/data/dataSource/upload_ogms/shp.py", String.valueOf(path), String.valueOf(picId) };
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
            dataContainer.downLoadFile(response, picFile, oid + ".png");
            log.info("首次下载成功");
        }else {
            //将生成的文件进行下载
            File picFile = new File(visualPath + "/" + oid + ".png");
            dataContainer.downLoadFile(response, picFile, oid + ".png");
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
                result.setMsg("dataTemplateId not exist!!!");
                result.setCode(-1);
                return result;
            }else {
                bulkDataLink.setDataTemplateId(templateId);
                bulkDataLinkDao.save(bulkDataLink);
                result.setMsg("edit success");
                result.setCode(0);
                result.setData("oid is "+ oid);
                return result;
            }
        }else {
            //判断oid的configFile是否为false
            if (bulkDataLink.getConfigFile()) {
                result.setCode(-1);
                result.setMsg("Only data without template id can be added");
                return result;
            } else {
                //新增templateId
                bulkDataLink.setDataTemplateId(templateId);
                bulkDataLinkDao.save(bulkDataLink);
                result.setCode(0);
                result.setMsg("add success");
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

}