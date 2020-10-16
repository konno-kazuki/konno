package jp.co.fujielectric.fss.data;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class VotStatus {
    public enum EnmStatus {
        Queued,
        Processing,
        Done,
        Error,
        Blocked,
        LimitExceeded
    }

    public String Status = "";
}
