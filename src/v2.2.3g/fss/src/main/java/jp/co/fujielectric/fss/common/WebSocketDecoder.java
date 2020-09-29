package jp.co.fujielectric.fss.common;

import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.EndpointConfig;
import jp.co.fujielectric.fss.data.WebSocketData;
import jp.co.fujielectric.fss.util.JsonUtil;

public class WebSocketDecoder implements Decoder.Text<WebSocketData> {
    @Override
    public void init(EndpointConfig endpointConfig) {
    }

    @Override
    public boolean willDecode(String jsonString) {
        return true;        // TODO: 変換判定はせず正常で戻す
    }

    @Override
    public WebSocketData decode(String jsonString) throws DecodeException {
        return JsonUtil.toObject(jsonString, WebSocketData.class);
    }

    @Override
    public void destroy() {
    }
}
