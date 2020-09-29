package jp.co.fujielectric.fss.common;

import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;
import jp.co.fujielectric.fss.data.WebSocketData;
import jp.co.fujielectric.fss.util.JsonUtil;

public class WebSocketEncoder implements Encoder.Text<WebSocketData> {
    @Override
    public void init(EndpointConfig endpointConfig) {
    }

    @Override
    public String encode(WebSocketData webSocketData) throws EncodeException {
        return JsonUtil.fromObject(webSocketData);
    }

    @Override
    public void destroy() {
    }
}
