package jp.co.fujielectric.fss.data;

import lombok.Getter;
import lombok.Setter;

public class SampleResult {

    public SampleResult(String id, String subject, String content, int contentNo) {
        this.id = id;
        this.subject = subject;
        this.content = content;
        this.contentNo = contentNo;
    }

    @Getter @Setter
    private String id;
    @Getter @Setter
    private String subject;
    @Getter @Setter
    private String content;
    @Getter @Setter
    private int contentNo;
}
