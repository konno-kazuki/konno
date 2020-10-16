package jp.co.fujielectric.fss.data;

import java.io.InputStream;
import javax.enterprise.context.RequestScoped;
import javax.faces.application.FacesMessage;
import lombok.Data;

/**
 * ファイルダウンロードクラス
 */
@Data
@RequestScoped
public class FileDownloadBean {

    /**
     * suffix
     */
    String suffix;
    /**
     * InputStream
     */
    InputStream inputStream;
    /**
     * ダウンロードファイル名
     */
    String downloadFileName;
    /**
     * メッセージ
     */
    FacesMessage facesMessage;
    /**
     * メッセージ（警告）
     */
    FacesMessage facesMessage2;

    // コンストラクタ
    public FileDownloadBean() {
        suffix = null;
        inputStream = null;
        downloadFileName = null;
        facesMessage = null;
        facesMessage2 = null;
    }
}
