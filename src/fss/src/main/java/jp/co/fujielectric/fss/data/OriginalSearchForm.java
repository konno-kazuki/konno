package jp.co.fujielectric.fss.data;

import lombok.Data;

@Data
public class OriginalSearchForm {
    public enum SearchOperate {
        and,
        or;
    };

    public enum SearchColumn {
        sender,
        receiver,
        time,
        subject,
        content,
        filename,
        error;
    };

    private String operate;
    private String column;
    private String word;

    public SearchOperate getSearchOperate() {
        return SearchOperate.valueOf(operate);
    }

    public SearchColumn getSearchColumn() {
        return SearchColumn.valueOf(column);
    }
}
