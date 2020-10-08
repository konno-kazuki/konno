package jp.co.fujielectric.fss.view;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.entity.ProcessingTimeReport;
import jp.co.fujielectric.fss.service.ProcessingTimeReportService;
import jp.co.fujielectric.fss.util.DateUtil;
import lombok.Getter;

/**
 * 処理時間記録ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class ProcessingTimeReportView extends ManageCommonView implements Serializable {

    /**
     * 処理時間記録リスト
     */
    @Getter
    private List<ProcessingTimeReport> processingTimeReportList;

    /**
     * 対象日時
     */
    @Getter
    private Date dispDate;

    @Inject
    private ProcessingTimeReportService processingTimeReportService;

    //コンストラクタ
    public ProcessingTimeReportView() {
        funcId = "processingTimeReport";
    }

    @PostConstruct
    @Override
    public void init() {
        super.init();
    }

    /**
     * マスタ設定値からの変数初期化
     *
     */
    @Override
    protected void initItems() {
        super.initItems();
    }

    /**
     * お知らせ情報を取得
     */
    @Override
    protected void getItemList() {
        
        dispDate = DateUtil.getSysDate();   //現在日時取得
        
        //当日の0:00
        Date fromDate = DateUtil.getDateExcludeTime(dispDate);
        //当日の23:59
        Date toDate = DateUtil.getDateExcludeMillisExpirationTime(fromDate);
        
        // 当日分の情報を取得する
        List<ProcessingTimeReport> getLst = processingTimeReportService.findAllByStartEnd(fromDate, toDate);
        
        //表示用リスト生成
        //当日0:00から12:59まで30分単位のデータを生成する
        processingTimeReportList = new ArrayList<>();
        Calendar calTmp = Calendar.getInstance();  //表示対象時刻
        calTmp.setTime(fromDate);
        Calendar calEnd = Calendar.getInstance();   //表示最終時刻
        calEnd.setTime(toDate);        
        while(calTmp.before(calEnd)){
            Date itemDate = calTmp.getTime();  //表示対象時刻（Date型）
            ProcessingTimeReport addItem = new ProcessingTimeReport();
            addItem.setMeasureTime(itemDate);   //計測日時
            addItem.setProcessingTime("");      //処理時間（取得できなかった場合のブランクを初期セット
            //DBから取得したリストから該当日時のデータを検索
            for(ProcessingTimeReport item: getLst){
                if(item.getMeasureTime().compareTo(itemDate) == 0){
                    //ヒット
                    addItem.setProcessingTime(item.getProcessingTime());  //処理時間
                    getLst.remove(item);    //次のループで検索対象が減るようにリストから除去しておく
                    break;
                }
            }
            processingTimeReportList.add(addItem);  //表示用リストに追加
            
            calTmp.add(Calendar.MINUTE, 30);   //表示対象時刻に30分追加
        }
    }
}
