package jp.co.fujielectric.fss.data;

import lombok.Data;

/**
 * Votiro レポートファイル情報
 */
@Data
public class VotReportInfo {
    /**
     * 無害化ファイル名
     */
    private String fileName;
    /**
     * アーカイブファイル1階層目のファイル名
     */
    private String childFileName;
    /**
     * ファイルタイプ
     */
    private String type;
    /**
     * 無害化結果ID
     * 10050100（ポリシーによるブロック）
     * 10050200（ウィルス検知によるブロック）
     * 10050500（無害化エンジンでのエラー）
     */
    private int id;
    /**
     * 詳細
     * ID=10050100の場合、Detailsの[]内を出力文字列として抽出
     */
    private String details;
}
