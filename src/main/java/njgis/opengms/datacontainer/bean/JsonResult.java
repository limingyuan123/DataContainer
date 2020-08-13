package njgis.opengms.datacontainer.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @Author mingyuan
 * @Date 2020.06.11 15:54
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JsonResult<T> implements Serializable {
    private static final long serialVersionUID = 1L;//JAVA序列化的机制是通过判断类的serialVersionUID来验证的版本一致的

    private Integer code=0;
    private String msg="success";
    private T data;
}
