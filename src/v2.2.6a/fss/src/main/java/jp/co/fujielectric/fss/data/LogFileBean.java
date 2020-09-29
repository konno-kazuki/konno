package jp.co.fujielectric.fss.data;

import java.io.File;
import java.io.Serializable;
import jp.co.fujielectric.fss.util.FileUtil;
import lombok.Data;

/**
 * 共有ファイルフォルダ一覧表示データクラス
 */
@Data
public class LogFileBean implements Serializable {

    /**
     * ファイルパス
     */
    private String filePath;

    /**
     * ファイル名称
     */
    private String fileName;

    /**
     * ファイルサイズ
     */
    private long size;

    /**
     * 種別=ファイル
     */
    private boolean file;

    /**
     * 種別=ディレクトリ
     */
    private boolean directory;

    /**
     * ファイル選択
     */
    private boolean checked;

    //コンストラクタ
    public LogFileBean() {
    }

    //コンストラクタ
    public LogFileBean(File file) {
        this.filePath = file.getPath();
        this.fileName = file.getName();
        this.size = file.length();
        this.file = file.isFile();
        this.directory = file.isDirectory();
        this.checked = false;
    }

    /**
     * オフィス系ファイルのアイコン取得
     *
     * @return オフィス系ファイルのアイコンパス
     */
    public String getFileIcon() {
        return FileUtil.getFileIconKind(fileName);
    }

    /**
     * 表示用ファイルサイズ取得
     *
     * @return 表示用ファイルサイズ
     */
    public String getSizeText() {
        return FileUtil.getSizeText(size);
    }
}
