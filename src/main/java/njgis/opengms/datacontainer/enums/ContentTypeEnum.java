package njgis.opengms.datacontainer.enums;

import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
@AllArgsConstructor
@Document
public enum ContentTypeEnum {
    //用于展示各种基于http的文件,可视化到网页
    HTML(0, "text/html;charset=utf-8"),
    Plain(1, "text/plain"),
    XML(2, "text/xml"),
    GIF(3, "image/gif"),
    JPG(4, "image/jpeg"),
    PNG(5, "image/png"),
    XHTML(6, "application/xhtml+xml"),
    XML_DATA(6, "application/xml"),
    Atom_XML(6, "application/atom+xml"),
    JSON(6, "application/json"),
    PDF(6, "application/pdf"),
    WORD(6, "application/msword"),
    OCTET_STREAM(6, "application/octet-stream"),
    X_WWW_FORM_URLENCODED(6, "application/x-www-form-urlencoded ");

    private int number;
    private String text;

    public String getText() {
        return text;
    }

    public static ContentTypeEnum getContentTypeByNum(int number){
        for(ContentTypeEnum contentTypeEnum:ContentTypeEnum.values()){
            if(contentTypeEnum.number==number){
                return contentTypeEnum;
            }
        }
        return null;
    }

    public static ContentTypeEnum getContentTypeByName(String name){
        for(ContentTypeEnum contentTypeEnum:ContentTypeEnum.values()){
            if(contentTypeEnum.name().toUpperCase().equals(name.toUpperCase())){
                return contentTypeEnum;
            }
        }
        return null;
    }
}
