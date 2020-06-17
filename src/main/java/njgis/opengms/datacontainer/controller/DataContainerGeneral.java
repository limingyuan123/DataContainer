package njgis.opengms.datacontainer.controller;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import njgis.opengms.datacontainer.bean.JsonResult;
import njgis.opengms.datacontainer.dao.DataListDao;
import njgis.opengms.datacontainer.dao.ImageDao;
import njgis.opengms.datacontainer.entity.DataList;
import njgis.opengms.datacontainer.entity.Image;
import njgis.opengms.datacontainer.service.DataContainer;
import njgis.opengms.datacontainer.utils.ResultUtils;
import njgis.opengms.datacontainer.utils.Utils;
import org.apache.catalina.servlet4preview.http.HttpServletRequest;
import org.dom4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;


import javax.servlet.http.HttpServletResponse;

import java.io.*;
import java.util.*;

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
    @Value("E:/upload")
    private String resourcePath;

    @Value("E:/upload/upload_ogms")
    private String ogmsPath;

    //upload网页
    @RequestMapping("/testUpload")
    public ModelAndView testUpload() {
        ModelAndView testUpload = new ModelAndView();
        testUpload.setViewName("testUpload");
        return testUpload;
    }

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

    //接口1 上传ogms数据
    @RequestMapping(value = "/uploadFile", method = RequestMethod.POST)
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
            jsonResult.setMsg("without name , userId ,origination, serverNode");
            return jsonResult;
        }

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
                //检查配置文件格式
                //String转xml，逐行读取配置文件内容
                Reader reader = null;
                reader = new InputStreamReader(files[files.length-1].getInputStream(),"utf-8");
                BufferedReader br = new BufferedReader(reader);
                String line;
                String content = "";
                while((line = br.readLine())!=null){
                    content += line;
                }
                Document configXML = DocumentHelper.parseText(content);
                //获取根元素
                Element root = configXML.getRootElement();
                //获取根元素下所有的子元素
                dataTemplateId = root.element("DataTemplate").getText();
                DataTemplateType = root.element("DataTemplate").attribute("type").getText();

                //首先初始化ogmsPath
                ogmsPath = "E:/upload/upload_ogms";
                ogmsPath = ogmsPath + "/" + uuid;
                //首先判断文件个数,一个文件也压缩上传
//                if (files.length==2){
//                    loadFileLog = dataContainer.uploadOGMS(ogmsPath,files);
//                    String singleFileName = files[0].getOriginalFilename();
//                    dataList.setSingleFileName(singleFileName);
//                }else {
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
//                }
            }
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
        return ResultUtils.success(jsonResult);
    }
    //接口2 下载数据
    @RequestMapping(value = "/data", method = RequestMethod.GET)
    public JsonResult downLoadFile(@RequestParam String uid, HttpServletResponse response){
        boolean downLoadLog = false;

        DataList dataList = dataListDao.findFirstByUid(uid);
        String downLoadPath = dataList.getPath();
        String fileName = dataList.getUid() + ".zip";
        File file = new File(downLoadPath + "/" + fileName);
        if (file.exists()) {
            response.setContentType("application/force-download");
            response.addHeader("Content-Disposition", "attachment;fileName=" + fileName);
            byte[] buffer = new byte[1024];
            FileInputStream fis = null;
            BufferedInputStream bis = null;
            try {
                fis = new FileInputStream(file);
                bis = new BufferedInputStream(fis);
                OutputStream outputStream = response.getOutputStream();
                int i = bis.read(buffer);
                while (i != -1) {
                    outputStream.write(buffer, 0, i);
                    i = bis.read(buffer);
                }
                downLoadLog = true;
                //return "下载成功";
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (bis != null) {
                    try {
                        bis.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        JsonResult jsonResult = new JsonResult();
        if (downLoadLog == true){
            jsonResult.setMsg("download success");;
            jsonResult.setCode(0);
        }else {
            jsonResult.setMsg("download fail");
            jsonResult.setCode(-1);
        }
        return ResultUtils.success(jsonResult);
    }

    //接口 删除指定上传数据
    @RequestMapping(value = "del", method = RequestMethod.DELETE)
    public JsonResult del(@RequestParam String uid){
        JsonResult jsonResult = new JsonResult();
        boolean delLog = false;
        DataList dataList = dataListDao.findFirstByUid(uid);
        String delPath = dataList.getPath();

        //删除文件夹或文件，包括删除文件夹下所有文件
        delLog = dataContainer.DeleteFolder(delPath);
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

        return ResultUtils.success(jsonResult);
    }



}