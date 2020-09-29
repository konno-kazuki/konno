package jp.co.fujielectric.fss.logic;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import javax.enterprise.context.RequestScoped;
import lombok.Data;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

/**
 * ダウンロードファイルデータクラス
 */
@Data
@RequestScoped
public class FileDownload implements Serializable {

	/**
	 * 改行マーク
	 */
	public static final String RETURN_MARK_WIN = "\r\n";

    /**
	 * 区切り文字
	 */
	private static final String	 PAUSE = ",";

	/**
	 * 文字列配列
	 */
    private ArrayList<String[]>		_ltarStr;

	/**
	 * 配列
	 */
    private ArrayList<String>		_ltStr;

    /**
	 * コンストラクタ
	 */
    public FileDownload() {
        _ltarStr = new ArrayList<>();
        _ltStr = new ArrayList<>();
    }

	/**
	 * 用意した文字列の配列を返す。
	 *
	 * @return 文字列配列
	 */
	public ArrayList<String[]> getList( ) {
		return _ltarStr;
	}

    /**
	 * 用意した文字列配列数を返す。
	 *
	 * @return 文字列配列数
	 */
	public int getListCnt( ) {
        if (_ltarStr!=null) {
            return _ltarStr.size();
        }
		return -1;
	}

	/**
	 * １つの文字列データを追加します。
	 *
	 * @param s1 追加したいString型データ
	 */
	public void addOneData( String s1 ) {
		_ltStr.add( s1 );
	}

	/**
	 * １つの整数データを追加します。
	 *
	 * @param i1 追加したいlong型データ
	 */
	public void addOneData( long i1 ) {
		_ltStr.add( String.valueOf(i1) );
	}

	/**
	 * １つの整数データを指定されたフォーマットにより変換した後、リストに追加します。
	 *
	 * @param i1 追加したいlong型データ
	 * @param sFormat フォーマット
	 */
	public void addOneData( long i1, String sFormat ) {
		DecimalFormat df = new DecimalFormat( sFormat );
		_ltStr.add( df.format(i1) );
	}

	/**
	 * １行のデータの書き込みを終え、改行処理を行います。
	 *
	 */
	public void addNewLine( ) {
		int iSize = _ltStr.size();
		if ( iSize == 0 ) {
			_ltarStr.add( null );

		} else {
			String[] as = _ltStr.toArray( new String[ iSize ] );
			_ltarStr.add( as );
			_ltStr.clear();
		}
	}

    /**
	 * 文字列配列を、CSV形式に変換
     *
     * @return CSV形式-文字列配列
	 */
    private String getConvertCsvDatas() {

        if (this.getList()!=null && this.getList().size()>0) {

            StringBuilder sb = new StringBuilder();
            int workLine = 0;
            for (String[] lineData: this.getList()) {
                if( lineData != null ) {
                    for (int i = 0; i < lineData.length; i++) {
                        String data = (lineData[i] == null) ? "": lineData[i];
                        if ( data != null ) {
                            sb.append(data);
                        }
                        if ( i != lineData.length - 1 ) {	// 最後でない場合
                            sb.append(PAUSE);
                        }
                    }
                }
                // 最終行のみ改行しない
                workLine++;
                if(workLine < this.getList().size()) {
                    sb.append(RETURN_MARK_WIN);
                }
            }

            return sb.toString();
        }

        return null;
    }

    /**
     * ダウンロードファイルを作成
     *
     * @param fileName          出力ファイル名
     *
     * @return StreamedContent
     * @throws java.lang.Exception
     */
    public StreamedContent getDownloadFile(String fileName) throws Exception {

        StreamedContent _downloadFile = null;

        if (this.getList()!=null && this.getList().size()>0) {
            try {
                //ファイル名
                String _fileName = new String(fileName.getBytes("MS932"), "ISO-8859-1");

                //文字列の配列を、CSV形式に変換
                String outStr = getConvertCsvDatas();

                //downloadFileにセット
                InputStream stream = new ByteArrayInputStream(outStr.getBytes("MS932"));
                _downloadFile = new DefaultStreamedContent(
                        stream,
                        "text/plain",
                        _fileName
                    );

            } catch (Exception ex) {
                throw ex;
            }
            finally {
                //後処理
            }
        }

        return _downloadFile;
    }
}
