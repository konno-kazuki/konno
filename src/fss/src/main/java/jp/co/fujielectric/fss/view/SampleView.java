package jp.co.fujielectric.fss.view;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.batch.operations.JobOperator;
import javax.batch.runtime.BatchRuntime;
import javax.ejb.EJB;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.ViewTrace;
import jp.co.fujielectric.fss.data.Item;
import jp.co.fujielectric.fss.ejb.SampleEjbAsync;
import jp.co.fujielectric.fss.entity.OnceUser;
import jp.co.fujielectric.fss.entity.Sample;
import jp.co.fujielectric.fss.entity.SampleSubject;
import jp.co.fujielectric.fss.data.CommonBean;
import jp.co.fujielectric.fss.data.OriginalSearchResult;
import jp.co.fujielectric.fss.data.SampleResult;
import jp.co.fujielectric.fss.logic.ItemHelper;
import jp.co.fujielectric.fss.logic.OriginalSearchLogic;
import jp.co.fujielectric.fss.service.OnceUserService;
import jp.co.fujielectric.fss.logic.RestManager;
import jp.co.fujielectric.fss.logic.SampleLogic;
import jp.co.fujielectric.fss.service.SampleService;
import jp.co.fujielectric.fss.logic.SyncFilesHelper;
import jp.co.fujielectric.fss.util.CommonUtil;
import lombok.Getter;
import lombok.Setter;

@AppTrace
@ViewTrace
@Named
@ViewScoped
public class SampleView implements Serializable {

    @Getter
    @Setter
    private int id;

    @Getter
    @Setter
    private String name;

    @Getter
    @Setter
    private String type;

    @Getter
    @Setter
    private String findName;

    @Inject
    private CommonBean commonBean;

    @Inject
    private SampleService sampleService;

    @PostConstruct
    public void initView() {
        System.out.println("SampleView Init!!!");
    }

    public void runBatch() {
        System.out.println("SampleView runBatch!!!");
        JobOperator job = BatchRuntime.getJobOperator();
        long id = job.start("sample-job", null);
    }

    public void insertSample() {
        sampleService.insertSample(id, name, type);
    }

    public void updateSample() {
        sampleService.updateSample(id, name, type);
    }

    public void deleteSample() {
        sampleService.deleteSample(id);
    }

    public void findSampleName() {
        Sample sample = sampleService.findSampleName(findName);
        id = sample.getId();
        name = sample.getName();
        type = sample.getType();
    }

    @Getter
    @Setter
    private String subjectId;

    @Getter
    @Setter
    private String subjectDisp;

    public void findSampleSubject() {
        SampleSubject ss = sampleService.findSampleSubject(subjectId);
        if (ss != null) {
            subjectDisp = ss.toString();
        } else {
            subjectDisp = "not found";
        }
    }

    @Getter
    @Setter
    private String subjectDispID;

    public void createSampleSubject() {
        subjectDispID = String.valueOf(sampleService.createSampleSubject());
    }

    @EJB
    SampleEjbAsync sampleEjbAsync;

    public void runEjbAsync() {
        System.out.println("SampleView runEjbAsync:before" + new Date().toString());
        sampleEjbAsync.asyncProcess();
        sampleEjbAsync.asyncProcess();
        System.out.println("SampleView runEjbAsync:after" + new Date().toString());
    }

    public void runEjbTimer() {
        System.out.println("SampleView runEjbTimer:before" + new Date().toString());
        sampleEjbAsync.timerProcess();
        System.out.println("SampleView runEjbTimer:after" + new Date().toString());
    }

    @Getter
    @Setter
    private String bindValue;

    @Getter
    @Setter
    private String onceUserId;
    @Getter
    @Setter
    private String urlString;

    public void createUrlString() {
        urlString = CommonUtil.createOnetimeUrl(commonBean.getRegionId(), onceUserId, true);
    }

    @Inject
    OnceUserService onceUserService;

    @Getter
    @Setter
    private String onceUrlParam;
    @Getter
    @Setter
    private List<Item> onceInfoList;

    public void findOnceInfo() {
        onceInfoList = new ArrayList<>();
        String onceId = CommonUtil.decodeBase64(onceUrlParam);
        OnceUser ou = onceUserService.find(onceId);
        if (ou != null) {
            onceInfoList.add(new Item("OnceID", onceId));
            onceInfoList.add(new Item("遷移先", ou.getTarget()));
            onceInfoList.add(new Item("InfoID", ou.getMailId()));
            onceInfoList.add(new Item("パスワード", ou.getPassword()));
            onceInfoList.add(new Item("アドレス", ou.getMailAddress()));
            onceInfoList.add(new Item("期限", ou.getExpirationTime().toString()));
        } else {
            onceInfoList.add(new Item("OnceID", onceId + " is NotFound!"));
        }
    }

    @Inject
    ItemHelper itemHelper;

    @Getter
    @Setter
    private List<Item> itemList = new ArrayList<>();

    public void findItem() {
        itemList.add(itemHelper.find(Item.REGION_ID, "sample"));
        itemList.add(itemHelper.find(Item.REGION_NAME, "sample"));
        itemList.add(itemHelper.find(Item.REGION_DOMAIN, "sample"));
        itemList.add(itemHelper.find(Item.FILE_SIZE_LIMIT, "sample"));
        itemList.add(itemHelper.find(Item.EXPIRATION_DEFAULT, "sample"));
    }

    @Inject
    private SyncFilesHelper syncFilesService;
    @Getter @Setter private String webSocketFile;

    public void webSocketTest() {
        syncFilesService.syncFile(webSocketFile);
    }

    @Inject
    private RestManager restManager;

    public void restTest() {
//        Sample sample = restManager.find("sample", "5", Sample.class);
//
//        sample.setId(777);
//
//        restManager.create("sample", sample);
//
//        sample.setName("更新してみた");
//
//        restManager.edit("sample", sample);
//
//        restManager.remove("sample", "999");    // 事前にデータを作っておく
//
//        System.out.println(sample);
    }

    @Inject
    private SampleLogic sampleLogic;
    @Getter @Setter private String sendInfoId;
    public void recodeLockTest() {
        try {
            sampleLogic.updateSendInfo(sendInfoId);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void sampleQuery() {
        System.out.println("---sampleQuery start!---");
        List<Sample> samples = sampleService.sampleQuery(name ,type);
        for(Sample sample : samples) {
            System.out.println("id:" + sample.getId() + ",name:" + sample.getName() + ",type:" + sample.getType());
        }
        System.out.println("---sampleQuery end!---");
    }

    @Getter @Setter
    private String subject;
    @Getter @Setter
    private String content;
    @Getter @Setter
    private String result;
    public void sampleJoinQuery() {
        System.out.println("---sampleJoinQuery start!---");
        List<SampleResult> samples = sampleService.sampleJoinQuery(subject ,content);
        result = "";
        samples.stream().forEach((sr) -> {
            result += "id:"+sr.getId()+",subject:"+sr.getSubject()+",content:"+sr.getContent()+",contentno:"+sr.getContentNo() + "\r\n";
        });
        System.out.println(result);
        System.out.println("---sampleJoinQuery end!---");
    }

    @Inject
    OriginalSearchLogic originalSearchLogic;
    public void searchOriginalQuery() {
        System.out.println("---searchOriginalQuery start!---");
        List<OriginalSearchResult> results = originalSearchLogic.searchOriginal(null);
        result = results.size() + "\r\n";

        results.stream().forEach((r) -> {
            result += "data:" + r.getSendTime() + r.getSendMailAddress() + r.getReceiveMailAddress() + "\r\n";
        });
        System.out.println("---searchOriginalQuery end!---");
    }
}
