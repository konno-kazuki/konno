package jp.co.fujielectric.fss.data;

import jp.co.fujielectric.fss.entity.ReceiveInfo;
import lombok.Data;

/**
 * パスワード解除データクラス
 */
@Data
public class PasswordUnlockBean {
    private ReceiveInfo receiveInfo;
    private boolean unlocked = false;
}
