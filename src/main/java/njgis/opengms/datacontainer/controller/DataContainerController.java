package njgis.opengms.datacontainer.controller;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import njgis.opengms.datacontainer.bean.JsonResult;
import njgis.opengms.datacontainer.dao.BulkDataLinkDao;
import njgis.opengms.datacontainer.dao.DataListComDao;
import njgis.opengms.datacontainer.dao.DataListDao;
import njgis.opengms.datacontainer.dao.ImageDao;
import njgis.opengms.datacontainer.entity.BulkDataLink;
import njgis.opengms.datacontainer.entity.DataListCom;
import njgis.opengms.datacontainer.entity.Image;
import njgis.opengms.datacontainer.service.DataContainer;
import njgis.opengms.datacontainer.utils.Utils;
import org.dom4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
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

    @Value("${resourcePath}")
    private String resourcePath;

    //upload网页
    @RequestMapping("/testUpload")
    public ModelAndView testUpload() {
        ModelAndView testUpload = new ModelAndView();
        testUpload.setViewName("testUpload");
        return testUpload;
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
            //有多个文件，且含有配置文件
            if (Objects.equals(files[files.length - 1].getOriginalFilename(), "config.udxcfg")){
                //检查配置文件格式 ,通过String转xml，逐行读取配置文件内容
                Reader reader = null;
                reader = new InputStreamReader(files[files.length-1].getInputStream(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(reader);
                String line;
                StringBuilder content = new StringBuilder();
                while((line = br.readLine())!=null){
                    content.append(line);
                }
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
                //获取根元素下所有的子元素
                dataTemplateId = root.element("DataTemplate").getText();
                DataTemplateType = root.element("DataTemplate").attribute("type").getText();
                //首先判断文件个数,一个文件也压缩上传
                loadFileLog = dataContainer.uploadOGMSMulti(bulkDataLink,ogmsPath,uuid,files,configExist);
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
                bulkDataLink.setDataTemplate(dataTemplateId);
                bulkDataLink.setType("template");
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
    public void downLoadFile(@RequestParam String oid, HttpServletResponse response) throws UnsupportedEncodingException {
        boolean downLoadLog = false;
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
    public JsonResult del(@RequestParam(value = "oid") String oid){
        JsonResult jsonResult = new JsonResult();
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
    @RequestMapping(value = "/bulkDownLoad",method = RequestMethod.GET)
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

    //断点续传接口Breakpoint continuation
    @RequestMapping(value = "dataBPContinue", method = RequestMethod.GET)
    public void dataBPContinue(@RequestParam String oid,HttpServletResponse response){

    }


    //以本名上传


}