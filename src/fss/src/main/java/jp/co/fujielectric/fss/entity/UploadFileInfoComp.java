package jp.co.fujielectric.fss.entity;

import java.io.Serializable;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import lombok.Data;

@Entity
@Data
@NamedQueries({
    @NamedQuery(name = "findFileInfoCompByGroup", query = "from UploadFileInfoComp where id = :id")
})
public class UploadFileInfoComp implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * アップロード処理単位の固有ID
     */
    @Column
    private String id;

    /**
     * ユニークID
     * v2.2.1でVotiroから返却されるIDからユニークIDに変更。
     */
    @Id
    @Column
    private String fileId;

    /**
     * ファイル名
     */
    @Column
    private String fileName;

    /**
     * 無害化ステータス
     */
    @Column
    private String Status;

    /**
     * 無害化完了フラグ
     */
    @Column
    private boolean sanitizeFlg;

    //[v2.2.1]
    /**
     * 処理担当ノード
     */
    @Column
    private String owner;
    
    //[v2.2.1]
    /**
     * SandBlast対応区分
     * 0：SandBlast 不使用。　（例：その他団体）	
     * 1：SandBlast 使用。　VotiroアップロードにSandBlast無害化ファイルを使用しない。　（例：静岡）	
     * 2：SandBlast 使用。　VotiroアップロードにSandBlast無害化ファイルを使用する。　　（例：京都）	
     */
    @Column
    private int sandBlastKbn;
    
    //[v2.2.1]
    /**
     * VOTIROにアップロードした際返却されるリクエストID
     */
    @Column
    private String requestId;

    //[v2.2.1]
    /**
     * 処理ステップ
     * 各ポーリングでの対象処理の検索、同ファイル進行中チェック等に使用
     * 1：無害化開始待ち
     * 2：SandBlastアップロード中
     * 3：アップロード待ち
     * 4：アップロード中
     * 5：ダウンロード待ち
     * 6：ダウンロード済み
     * 7：無害化完了
     * 8：キャンセル	
     */
    @Column
    private int step;      

    //[v2.2.1]
    /**
     * リトライカウント
     */
    @Column
    private int retryCount;      

    //[v2.2.1]
    /**
     * パスワード解除状態
     * 0:パスワード無しファイル　(パスワード無しZIP パスワードファイル含まず）
     * 2:パスワード付きファイル　パスワード解除済み (パスワードファイルを含まないパスワード付きZIP　パスワード解除済み）
     * 3:パスワード付きファイル　パスワード未解除 (パスワード付きZIP　パスワード未解除）
     * 4:パスワード付きファイル入りZIPファイル　全ファイル解除済み
     * 5:パスワード付きファイル入りZIPファイル　パスワード未解除あり
     */
    @Column
    private int decryptKbn;      

    //[v2.2.1]
    /**
     * 受信情報ID
     */
    @Column
    private String receiveInfoId;
    
    //[v2.2.1]
    /**
     * 送信ファイルID
     */
    @Column
    private String sendFileId;

    //[v2.2.1]
    /**
     * 受信ファイルID
     */
    @Column
    private String receiveFileId;

    //[v2.2.1]
    /**
     * votiroアップロードファイルパス
     * Votiroへアップロードするファイルのフルパス。
     */
    @Column(columnDefinition = "TEXT")
    private String uploadFilePath;

    //[v2.2.1]
    /**
     * votiroファイルパス
     * Votiroからダウンロードしたファイルの後処理前のフルパス。
     */
    @Column(columnDefinition = "TEXT")
    private String votiroFilePath;

    //[v2.2.1]
    /**
     * 元ファイル名
     */
    @Column
    private String fileNameOrg;

    //[v2.2.1]
    /**
     * エラー情報
     */
    @Column
    private String errorInfo;

    //[v2.2.1]
    /**
     * 登録日時
     */    
    @Temporal(TemporalType.TIMESTAMP)
    private Date insertDate;

    //[v2.2.1]
    /**
     * 更新日時
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateDate;

    //[v2.2.2]
    /**
     * SandBlast無害化結果
     */
    @Column(columnDefinition = "TEXT")
    private String sandBlastResult;    

    //[v2.2.2]
    /**
     * SandBlastメールID
     */
    @Column
    private String sandblastMailId;
    
    //[v2.2.3]
    /**
     * Sandblast要求日時
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date sandblastStartDate;

    //[v2.2.3]
    /**
     * Sandblast完了日時
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date sandblastCompDate;

    //[v2.2.3]
    /**
     * Votiro要求日時
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date votiroStartDate;

    //[v2.2.3]
    /**
     * Votiro完了日時
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date votiroCompDate;

    //[v2.2.3]
    /**
     * VotiroIPアドレス
     */
    @Column(length = 32)
    private String votiroIP;

    //[v2.2.3]
    /**
     * レポートファイルパス
     * ファイルの無害化失敗、アーカイブファイルの際に取得したレポートファイルのフルパス。
     */
    @Column(columnDefinition = "TEXT")
    private String reportFilePath;

    //[v2.2.3]
    /**
     * エラー
     * ０：正常（エラー無し）
     * ２：Votiroへのファイルアップロード時の異常
     * ３：Votiroでの無害化時の異常
     * ４：Votiroの監視、ファイルダウンロード時の異常
     */
    @Column
    private int errInfo;

    //[v2.2.3]
    /**
     * エラー詳細
     * エラーが「２：Votiroへのファイルアップロード時の異常」の場合
     * 	HTTPのレスポンスコードを格納
     * エラーが「３：Votiroでの無害化時の異常」の場合
     * 	検索されたidにより設定
     * 		10050100（ポリシーによるブロック）の場合
     * 			”10050100” + Detailsの[]内の文字列
     * 		10050200（ウィルス検知によるブロック）の場合
     * 			”10050200”
     * 		10050500（無害化エンジンでのエラー）の場合
     * 			”10050500”
     * 		50070050（怪しいファイル構造検知によりブロック）の場合  [v2.2.6]追加
     * 			”50070050”
     * 		（アーカイブファイルの１階層目で検出した場合は
     * 			最初に検出されたものを格納）
     * 　　　　　※「10050100」以外はmailmessageテーブルのFuncID="delete_reason_details"のitemKeyに登録されているものを対象とする。[v2.2.6]追加
     * エラーが「４：Votiroの監視、ファイルダウンロード時の異常」の場合
     * 	HTTPのレスポンスコードを格納
     */
    @Column(columnDefinition = "TEXT")
    private String errDetails;

    //[v2.2.3]
    /**
     * エラーファイル
     * 1：対象ファイルで異常検出
     * 2：アーカイブファイル内のファイルで異常検出
     */
    @Column
    private int errFile;

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof UploadFileInfoComp)) {
            return false;
        }
        UploadFileInfoComp other = (UploadFileInfoComp) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "jp.co.fujielectric.fss.entity.UploadStatus[ id=" + id + " ]";
    }

}
