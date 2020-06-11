package njgis.opengms.datacontainer.controller;

import njgis.opengms.datacontainer.bean.JsonResult;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * @Auther mingyuan
 * @Data 2020.06.11 15:46
 */
@Controller
@RequestMapping(value = "/general")
public class DataContainerGeneral {
    @RequestMapping(value = "/upload", method = RequestMethod.POST)
    public JsonResult uploadSingle(){
        JsonResult jsonResult = new JsonResult();
        return jsonResult;
    }
}
