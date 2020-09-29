package jp.co.fujielectric.fss.data;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * post情報クラス
 */
@Data
public class PostInfoBean {
    private String sendInfoId;
    private String receiveInfoId;
    private List<String> fileNames = new ArrayList<>();
}
