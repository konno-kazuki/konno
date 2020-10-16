package jp.co.fujielectric.fss.data;

import java.io.Serializable;
import javax.enterprise.context.RequestScoped;
import javax.inject.Named;
import lombok.Data;

/**
 * ワンタイム情報格納Bean
 * 一時的なワンタイム情報渡しに利用する
 */
@Named
@RequestScoped
@Data
@SuppressWarnings("serial")
public class OnetimeBean implements Serializable {
    private String onetimeId;
    private String target;
}
