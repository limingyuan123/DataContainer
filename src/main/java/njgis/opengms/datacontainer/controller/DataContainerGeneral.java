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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import static njgis.opengms.datacontainer.utils.Utils.saveFiles;

import java.io.*;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @Auther mingyuan
 * @Data 2020.06.11 15:46
 */
@Controller
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

    @RequestMapping(value = "/uploadFile", method = RequestMethod.POST)
    public JsonResult uploadFile(@RequestParam("ogmsdata") MultipartFile[] files,
                                 @RequestParam("name")String uploadName,
                                 @RequestParam("userId")String userName,
                                 @RequestParam("serverNode")String serverNode,
                                 @RequestParam("origination")String origination,
                                 HttpServletRequest request)throws IOException {
        JsonResult jsonResult = new JsonResult();
        BufferedInputStream bis = null;
        FileOutputStream fos = null;
        ZipOutputStream zos = null;
        InputStream inputStream = null;

        Date now = new Date();

        //参数检验
        if (uploadName==""||userName==""||serverNode==""||origination==""){
            jsonResult.setMsg("without name , userId ,origination, serverNode");
            jsonResult.setCode(-1);
            return jsonResult;
        }
        String uuid = UUID.randomUUID().toString();
        ogmsPath = ogmsPath + "/" + uuid;
        boolean localFile = false;
        DataList dataList = new DataList();
        //首先判断文件个数
        if (files.length==2){
            localFile = dataContainer.uploadOGMS(ogmsPath,files);
            String singleFileName = files[0].getOriginalFilename();
            dataList.setSingleFileName(singleFileName);
        }else {
            try {
                File filePath = new File(ogmsPath);
                if (!filePath.exists()){
                    filePath.mkdirs();
                }
                File zip = File.createTempFile(uuid, ".zip",filePath);
                zos = new ZipOutputStream(new FileOutputStream(zip));
                byte[] bufs = new byte[1024 * 10];

                List<String> fileList = new ArrayList<>();
                //如果为多个，则将上传的文件进行压缩，之后进行上传，配置文件不压缩
                for (int i = 0; i < files.length; i++) {
                    fileList.add(ogmsPath + "/" + files[i].getOriginalFilename());
                    inputStream = files[i].getInputStream();
                    String streamfilename = files[i].getOriginalFilename();

                    ZipEntry zipEntry = new ZipEntry(streamfilename);
                    zos.putNextEntry(zipEntry);

                    bis = new BufferedInputStream(inputStream, 1024 * 10);
                    int read = 0;
                    while ((read = bis.read(bufs, 0, 1024 * 10)) != -1) {
                        zos.write(bufs, 0, read);
                    }
                }

                dataList.setFileList(fileList);
                zos.flush();
                zos.close();

                inputStream = new FileInputStream(zip);
            }catch (FileNotFoundException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            } finally {
                // 关闭流
                try {
                    if (null != bis)
                        bis.close();
                    if (null != zos)
                        zos.close();

                } catch (IOException e) {
                    log.error("InputStream or OutputStream close error : {}", e);
                }
            }
            //在该文件夹内，仍存储未压缩文件
            localFile = dataContainer.uploadOGMS(ogmsPath,files);
        }

        if (localFile == true){
            //信息入库
            dataList.setDate(now);
            dataList.setName(uploadName);
            dataList.setOrigination(origination);
            dataList.setServerNode(serverNode);
            dataList.setPath(ogmsPath);
            dataList.setUserId(userName);
            dataListDao.insert(dataList);


            jsonResult.setCode(0);
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("source_store_id",uuid);
            jsonObject.put("file_name",uploadName);
            jsonResult.setData(jsonObject);
        }
        return ResultUtils.success(jsonResult);
    }
}