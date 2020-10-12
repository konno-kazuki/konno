package jp.co.fujielectric.fss.batch;

import javax.batch.api.Batchlet;
import javax.enterprise.context.Dependent;
import javax.inject.Named;

@Named
@Dependent
public class TestBatchlet implements Batchlet {
    // コンストラクタ（CDIのために必ず必要）
    public TestBatchlet() {}

    @Override
    public String process() throws Exception {
        System.out.println("[Batchlet] process");
        return null;
    }

    @Override
    public void stop() throws Exception {
        System.out.println("[Batchlet] stop");
    }
}
