package jp.co.fujielectric.fss.view;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.entity.MonthlyReport;
import jp.co.fujielectric.fss.service.MonthlyReportService;
import lombok.Getter;
import lombok.Setter;

/**
 * 月報表示ビュークラス (BackingBean)
 */
@AppTrace
@ViewTrace
@Named
@ViewScoped
public class MonthlyReportView extends ManageCommonView implements Serializable {
    
    @Inject
    private MonthlyReportService monthlyReportService;
    
    @Getter
    @Setter
    private List<MonthlyReport> monthlyList;
    @Getter
    @Setter
    private List<MonthlyReport> filteredMonthlyList_1 = new ArrayList<>();
    @Getter
    @Setter
    private List<MonthlyReport> filteredMonthlyList_2 = new ArrayList<>();
    @Getter
    @Setter
    private List<MonthlyReport> filteredMonthlyList_3 = new ArrayList<>();
    
    @Getter
    protected String monthlyReportCharset;          //月報レポートCharset(=dataExporter用エンコード)
    
    //コンストラクタ
    public MonthlyReportView() {
        funcId = "monthlyReport";
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
        Item item;

        // 月報レポートCharset(=dataExporter用エンコード)
        item = itemHelper.find(Item.MONTHLY_REPORT_CHARSET, funcId);
        if (item!=null) {
            monthlyReportCharset = item.getValue();
        }
        
        super.initItems();
    }

    /**
     * 画面区分毎の初期化
     *
     */
    @Override
    public void initFunc() {
        
        super.initFunc();
    }
    
    /**
     * 「月報表示」情報を取得
     */
    @Override
    protected void getItemList() {
        
        // 月報情報
        monthlyList = new ArrayList<>();
        List<MonthlyReport> datas = monthlyReportService.findForMonthlyReportList();
        for (MonthlyReport dto : datas) {
            
//            MonthlyReportBean dto = new MonthlyReportBean();
//            dto.setYearmonth(mr.getYearmonth());
//            dto.setSla_vpn_vpn(mr.getSla_vpn_vpn());
//            dto.setSla_vpn_lg(mr.getSla_vpn_lg());
//            dto.setSla_lg_vpn(mr.getSla_lg_vpn());
//            dto.setSla_internet_f(mr.getSla_internet_f());
//            dto.setSla_lgwan_f(mr.getSla_lgwan_f());
//            dto.setDelaycount(mr.getDelaycount());
//            dto.setSendcount_m(mr.getSendcount_m());
//            dto.setRecvcount_m(mr.getRecvcount_m());
//            dto.setSanitizedfilecount_m(mr.getSanitizedfilecount_m());
//            dto.setDeletedfilecount_m(mr.getDeletedfilecount_m());
//            dto.setSendcount_f(mr.getSendcount_f());
//            dto.setRecvcount_f(mr.getRecvcount_f());
//            dto.setSanitizedfilecount_f(mr.getSanitizedfilecount_f());
//            dto.setDeletedfilecount_f(mr.getDeletedfilecount_f());
//            dto.setComfirmdate(mr.getComfirmdate());
            monthlyList.add(dto);
            
            //filteredMonthlyList
            filteredMonthlyList_1.add(dto);
            filteredMonthlyList_2.add(dto);
            filteredMonthlyList_3.add(dto);
        }
        //LOG.debug("getItemList:monthlyList.size=" + monthlyList.size());
    }

    /**
     * dataExporter用ファイル名取得(拡張子は不要）
     * 
     * @param key
     * @return ファイル名
     */
    public String getDataExporterFileName(String key) {
        Date _date = new Date();
        String _fileName = "_" + new SimpleDateFormat("yyyyMMdd").format(_date);
        String fileName = getItemCaption(key) + _fileName;
        return fileName;
    }
}
