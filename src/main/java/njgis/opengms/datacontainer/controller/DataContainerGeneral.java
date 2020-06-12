package njgis.opengms.datacontainer.controller;

import njgis.opengms.datacontainer.bean.JsonResult;
import njgis.opengms.datacontainer.dao.ImageDao;
import njgis.opengms.datacontainer.entity.Image;
import njgis.opengms.datacontainer.utils.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import javax.xml.crypto.Data;
import java.util.Date;
import java.util.UUID;

/**
 * @Auther mingyuan
 * @Data 2020.06.11 15:46
 */
@Controller
@RequestMapping(value = "/general")
public class DataContainerGeneral {
    @Autowired
    ImageDao imageDao;

    @Value("E:/upload")
    private String resourcePath;

    @RequestMapping(value = "/upload", method = RequestMethod.POST)
    public JsonResult uploadSingle(){
        JsonResult jsonResult = new JsonResult();

        return jsonResult;
    }

    @RequestMapping("/testUpload")
    public ModelAndView testUpload() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("testUpload");
        return mv;
    }

    @RequestMapping("/uploadImg")
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
}


