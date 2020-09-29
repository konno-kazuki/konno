package jp.co.fujielectric.fss.data;

import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * メール原本検索結果bean
 */
@Data
@AllArgsConstructor
public class OriginalSearchResult {
    String sendinfoId;
    String sendMailAddress;
    String receiveMailAddress;
    Date sendTime;
    Date expirationTime;
    String subject;
    String content;
    String procDate;    //[v2.2.1]
    Long fileCount;
    Long fileSize;
    
    public String getKey() {
        return sendinfoId + receiveMailAddress;
    }
}
