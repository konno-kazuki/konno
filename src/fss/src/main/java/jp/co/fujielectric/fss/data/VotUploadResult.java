package jp.co.fujielectric.fss.data;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class VotUploadResult {
    public String RequestID = "";
    public VotResultUsedRules UsedRules;
}
