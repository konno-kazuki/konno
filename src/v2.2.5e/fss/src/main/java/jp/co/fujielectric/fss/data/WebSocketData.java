package jp.co.fujielectric.fss.data;

import lombok.Data;

/**
 * WebSocket通信用データクラス
 */
@Data
public class WebSocketData {
    private String path;
    private int index;
    private byte[] dataPart;
    private String result;
    private String resultMessage;
}
