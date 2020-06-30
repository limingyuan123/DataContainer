package njgis.opengms.datacontainer.controller;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import njgis.opengms.datacontainer.bean.JsonResult;
import njgis.opengms.datacontainer.dao.DataListDao;
import njgis.opengms.datacontainer.dao.ImageDao;
import njgis.opengms.datacontainer.entity.DataList;
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
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Auther mingyuan
 * @Data 2020.06.11 15:46
 */
@RestController
@Slf4j
public class DataContainerGeneral {
    @Autowired
    ImageDao imageDao;

    @Autowired
    DataContainer dataContainer;

    @Autowired
    DataListDao dataListDao;

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

    //接口1 批量上传ogms数据  含配置文件类
    @RequestMapping(value = "/data", method = RequestMethod.POST)
    public JsonResult uploadFile(@RequestParam("ogmsdata")MultipartFile[] files,
                                 @RequestParam("name")String uploadName,
                                 @RequestParam("userId")String userName,
                                 @RequestParam("serverNode")String serverNode,
                                 @RequestParam("origination")String origination) throws IOException, DocumentException {
        JsonResult jsonResult = new JsonResult();
        boolean loadFileLog = false;
        Date now = new Date();
        DataList dataList = new DataList();
        String uuid = UUID.randomUUID().toString();
        List<String> fileList = new ArrayList<>();
        String dataTemplateId = "";
        String DataTemplateType = "";
        //参数检验
        if (uploadName==""||userName==""||serverNode==""||origination==""){
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
        }else if (files.length>1){
            //有多个文件，且含有配置文件
            if (files[files.length-1].getOriginalFilename().equals("config.udxcfg")){
                //检查配置文件格式 ,通过String转xml，逐行读取配置文件内容
                Reader reader = null;
                reader = new InputStreamReader(files[files.length-1].getInputStream(),"utf-8");
                BufferedReader br = new BufferedReader(reader);
                String line;
                String content = "";
                while((line = br.readLine())!=null){
                    content += line;
                }
                //用正则表达式匹配content是否包含xml非法字符  ' " > < &
                String pattern = ".*&.*";
                boolean isMatch = Pattern.matches(pattern,content);
                //如果含有非法字符，则用CDATA包裹
                if (isMatch){
                    //匹配头
                    String pattern1 = "<add";
                    Pattern p1 = Pattern.compile(pattern1);
                    Matcher m1 = p1.matcher(content);
                    content = m1.replaceAll("<![CDATA[<add");
                    //匹配尾
                    String pattern2 = "/>";
                    Pattern p2 = Pattern.compile(pattern2);
                    Matcher m2 = p2.matcher(content);
                    content = m2.replaceAll("/>]]>");
                }
                Document configXML = DocumentHelper.parseText(content);
                //获取根元素
                Element root = configXML.getRootElement();
                //获取根元素下所有的子元素
                dataTemplateId = root.element("DataTemplate").getText();
                DataTemplateType = root.element("DataTemplate").attribute("type").getText();
                //首先判断文件个数,一个文件也压缩上传
                loadFileLog = dataContainer.uploadOGMSMulti(ogmsPath,uuid,files);
                for (int i=0;i<files.length;i++){
                    //fileList字段也不加入配置文件
                    //对文文件的全名进行截取然后在后缀名进行删选。
                    int begin = files[i].getOriginalFilename().indexOf(".");
                    int last = files[i].getOriginalFilename().length();
                    //获得文件后缀名
                    String a = files[i].getOriginalFilename().substring(begin, last);
                    if (a.endsWith(".udxcfg")){
                        break;
                    }
                    fileList.add(ogmsPath + "/" + files[i].getOriginalFilename());
                }
                dataList.setFileList(fileList);
            }
        }else {
            //无配置文件
            loadFileLog = false;
            jsonResult.setCode(-1);
            jsonResult.setMsg("No config file");
            return jsonResult;
        }
        if (loadFileLog == true){
            //信息入库
            dataList.setDate(now);
            dataList.setName(uploadName);
            dataList.setOrigination(origination);
            dataList.setServerNode(serverNode);
            dataList.setPath(ogmsPath);
            dataList.setUserId(userName);
            dataList.setUid(uuid);
            if (DataTemplateType.equals("id")) {
                dataList.setDataTemplateId(dataTemplateId);
                dataList.setType("template");
            }else {
                dataList.setType(DataTemplateType);
            }
            dataListDao.insert(dataList);
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
        downLoadLog = dataContainer.downLoad(uid,response);

        JsonResult jsonResult = new JsonResult();
        if (downLoadLog == true){
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
        boolean delLog = false;
        DataList dataList = dataListDao.findFirstByUid(uid);
        String delPath = dataList.getPath();

        //删除文件夹或文件，包括删除文件夹下所有文件
        delLog = dataContainer.deleteFolder(delPath);
        if (delLog == true){
            jsonResult.setMsg("delete success");
            jsonResult.setCode(0);
            //删除对应的数据库内容
            if (dataList!=null) {
                dataListDao.delete(dataList);
            }
        }else {
            jsonResult.setCode(-1);
            jsonResult.setMsg("delete fail");
        }

        return jsonResult;
    }

    //接口4 批量下载
    @RequestMapping(value = "/BulkDownLoad",method = RequestMethod.GET)
    public void BulkDownLoad(@RequestBody List<String> uids, HttpServletResponse response) throws UnsupportedEncodingException {
        Boolean downLoadLog = false;
        Boolean delCacheFile = false;
        File[] files = new File[uids.size()];
        for (int i=0;i<uids.size();i++){
            DataList dataList = dataListDao.findFirstByUid(uids.get(i));
            String downLoadPath = dataList.getPath();
            if (dataList.getFileList().size() == 1){
                String fileSingle = dataList.getFileList().get(0);
                File file = new File(fileSingle);
                files[i] = file;
            }else {
                String fileZip = dataList.getUid() + ".zip";
                File file = new File(downLoadPath + "/" + fileZip);
                files[i] = file;
            }
        }
        JsonResult jsonResult = new JsonResult();
        jsonResult = dataContainer.downLoadBulkFile(response,files);
        if (jsonResult.getCode() == 0){
            //如果下载成功，则将打包存储在服务器的文件删除
            delCacheFile = dataContainer.deleteFolder(jsonResult.getData().toString());
            if (delCacheFile == false){
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

    //无需配置文件上传接口


}