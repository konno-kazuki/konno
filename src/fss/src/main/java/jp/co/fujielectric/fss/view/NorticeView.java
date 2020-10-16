/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.co.fujielectric.fss.view;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.inject.Named;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.entity.Nortice;
import jp.co.fujielectric.fss.logic.ItemHelper;
import jp.co.fujielectric.fss.service.NorticeService;
import org.apache.logging.log4j.Logger;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

/**
 * お知らせビュークラス (BackingBean)
 *
 */
@AppTrace
@ViewTrace
@Named
@RequestScoped
public class NorticeView implements Serializable {

    @Inject
    private NorticeService norticeService;
    
    @Inject
    protected ItemHelper itemHelper;

    @Inject
    protected Logger LOG;
    
    @Getter
    private List<Nortice> norticeList;

    @Getter
    @Setter
    private Nortice selectedNortice = null;

    //DispMessageキー（メッセージフォーマット）
    private static final String DSP_NORTICE_MESSAGE_FORMAT = "dspNorticeMessageFormat";

    //メッセージフォーマット
    private String msgFormat = "%t\n%s\n\n%c\n--------------------";
    
    private static final String LARGE_FONT = "x-large";    //大フォント
    
    // コンストラクタ
    @PostConstruct
    public void init() {
        // お知らせ情報
        norticeList = norticeService.findAllByToday();
        
        //お知らせ表示フォーマット初期化
        try {
            //お知らせ表示フォーマット取得
            msgFormat = itemHelper.findDispMessageStr(DSP_NORTICE_MESSAGE_FORMAT, Item.FUNC_COMMON);
            //フォーマット中の\nを改行に置換える
            msgFormat = StringUtils.replace(msgFormat, "\\n", "\n" );
        } catch (Exception e) {
            LOG.warn("# お知らせメッセージのフォーマット取得に失敗しました。Err:{}", e.toString());
        }            
    }
    
    /**
     * お知らせの画面表示用文字列取得
     * @param nortice
     * @return 
     */
    public String getNorticeMessage(Nortice nortice)
    {
        try {
            if(nortice == null)
                return "";
            String msg = msgFormat;
            SimpleDateFormat sdFormat = new SimpleDateFormat("yyyy/MM/dd"); //日付表示書式
            //フォーマット中の%t2を終了日に置換える
            msg = StringUtils.replace(msg, "%t2", sdFormat.format(nortice.getEndTime()));
            //フォーマット中の%t1または%tを開始日に置換える
            msg = StringUtils.replace(msg, "%t1", sdFormat.format(nortice.getStartTime()));
            msg = StringUtils.replace(msg, "%t", sdFormat.format(nortice.getStartTime()));

            //フォーマット中の%<を大フォント開始タグに置換える
            msg = StringUtils.replace(msg, "%<", "<span style=\"font-size:" + LARGE_FONT + ";\">");
            //フォーマット中の%>を大フォント終了タグに置換える
            msg = StringUtils.replace(msg, "%>", "</span>");
            
            //フォーマット中の%sを件名(subject)に置き換える
            msg = StringUtils.replace(msg, "%s", makeEscapeMessage(nortice.getSubject()));
            //フォーマット中の%cを本文(content)に置き換える
            msg = StringUtils.replace(msg, "%c", makeEscapeMessage(nortice.getContent()));
            return msg;
        } catch (Exception e) {
            LOG.warn("# お知らせメッセージ取得に失敗しました。(format:{}, Subject:{}, Content:{}) Err:{}",
                    msgFormat, nortice.getSubject(), nortice.getContent(), e.toString());
            return "";
        }
    }

    /**
     * HTML特殊文字をエスケープした文字列を生成
     * @param source
     * @return 
     */
    private String makeEscapeMessage(String source)
    {
        if(source == null)
            return "";
        //エスケープ処理
        return source.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;");        
    }
    
    /**
     * お知らせ情報の有無を返す
     *
     * @return true:お知らせ無し / false:お知らせ有り
     */
    public boolean isNorticeEmpty() {
        return norticeList.isEmpty();
    }

}
