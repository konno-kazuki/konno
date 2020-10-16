package jp.co.fujielectric.fss.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;
import javax.xml.bind.annotation.XmlRootElement;
import jp.co.fujielectric.fss.util.DateUtil;
import lombok.Data;

/**
 * 受信ファイルエンティティ<br>
 * 受信情報エンティティに紐付く送信ファイル、添付ファイルの情報を格納する<br>
 * 多対一： 受信情報<br>
 *
 * @author nakai
 */
@Entity
@Data
@SuppressWarnings("serial")
@NamedQueries({
    @NamedQuery(name = ReceiveFile.NAMED_QUEUE_FIND_SAMEFILE, 
            query = "SELECT DISTINCT a FROM ReceiveFile a LEFT JOIN FETCH a.receiveInfo "
                    + " WHERE a.sendFileId = :sendFileId AND a.decryptKbn = :decryptKbn "
                    + " AND ( a.sanitizeFlg = true OR a.receiveInfo.id = :receiveInfoId ) " //receiveInfoIDに時IDを指定することで、自分自身の前の結果も対象とする
                    + " AND a.result IN (1,2,4,5) " //キャンセル(6)、無害化なし(7)は対象外
                    + " ORDER BY a.result ASC"),  
})
@XmlRootElement
public class ReceiveFile implements Serializable {

    /**
     * 同一ファイルレコード検索用NamedQueue
     */
    public static final String NAMED_QUEUE_FIND_SAMEFILE = "ReceiveFile.findSameFile";
    
    /**
     * ID
     */
    @Id
    private String id;

    /**
     * 受信情報
     */
    @ManyToOne
    @JoinColumn(name = "receiveinfo_id")
    @JsonBackReference
    private ReceiveInfo receiveInfo;

    /**
     * ファイル名
     */
    @Column
    private String fileName;

    /**
     * ファイルパス（実体名）
     */
    @Column(columnDefinition = "TEXT")
    private String filePath = "";

    /**
     * ファイル形式
     */
    @Column
    private String fileFormat;

    /**
     * ファイルサイズ
     */
    @Column
    private long fileSize;

    /**
     * 破棄フラグ
     */
    @Column
    private boolean excludeFlg;

    /**
     * 無害化対象フラグ
     */
    @Column
    private boolean targetFlg;

    /**
     * 無害化済フラグ
     */
    @Column
    private boolean sanitizeFlg;

    /**
     * ファイルメッセージ
     */
    @Column
    private String fileMessage;

    /**
     * ダウンロード回数
     */
    @Column
    private long downloadCount;

    /**
     * 受信済フラグ
     */
    @Column
    private boolean receiveFlg;

    /**
     * 受信日時
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date receiveTime;

    /**
     * ZIP内文字コード変換対象
     */
    @Column
    private boolean zipCharsetConvert;

    /**
     * ZIP内文字コード変換結果
     */
    @Column
    private boolean zipCharsetConverted;

    //[v2.2.1]
    /**
     * 送信ファイルID
     * Votiro負荷軽減で同一ファイルかどうかの判定に利用
     */
    @Column
    private String sendFileId;
    
    //[v2.2.1]
    /**
     * パスワード解除状態		
     * 0:パスワード無しファイル
     * 2:パスワード付きファイル　パスワード解除済み
     * 3:パスワード付きファイル　パスワード未解除	
     * 4:パスワード付きファイル入りZIPファイル　全ファイル解除済み
     * 5:パスワード付きファイル入りZIPファイル　パスワード未解除あり
     * Votiro負荷軽減で同一ファイル判定に利用		
     */
    @Column
    private int decryptKbn;
    
    //[v2.2.1]
    /**
     * 無害化結果区分　（現状では無害化結果がわからないため）
     * 0:処理中
     * 1:無害化済み
     * 2:ブロック
     * 4:無害化エラー
     * 5:無害化対象外
     * 6:キャンセル
     */
    @Column
    private int result;

    //[v2.2.1]
    /**
     * votiroファイルパス
     * Votiroからダウンロードしたファイルの後処理前のフルパス。
     * 他ReceiveFileで流用する際に参照
     */
    @Column(columnDefinition = "TEXT")
    private String votiroFilePath;    
    
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

    //[v2.2.3]
    /**
     * エラーファイルコード
     * 0：正常（エラーなし）
     * 1：対象ファイルで異常検出
     * 2：アーカイブファイル内のファイルで異常検出
     */
    @Column
    private int fileErrCode;

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
    
    /**
     * パスワード解除ファイル
     */
    @Transient
    private List<DecryptFile> decryptFiles = new ArrayList<>();

    //[v2.2.1]
    /**
     * SendFile
     */
    @Transient
    private SendFile sendFile;
    
    // ファイルパスを返す
    @Override
    public String toString() {
        return filePath;
    }
    
    /**
     * 作成日付、更新日付に現在日時をセットする
     */
    @JsonIgnore
    public void resetDate(){
        //更新日付に現在日時をセットする
        updateDate = DateUtil.getSysDateExcludeMillis();   //更新日付
        if(insertDate == null)
            insertDate = updateDate;   //作成日付
    }    
}
