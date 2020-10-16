package jp.co.fujielectric.fss.batch;

import javax.annotation.PostConstruct;
import javax.batch.api.chunk.ItemProcessor;
import javax.enterprise.context.Dependent;
import javax.inject.Named;

@Named
@Dependent
public class TestItemProcessor implements ItemProcessor {
    // コンストラクタ（CDIのために必ず必要）
    public TestItemProcessor() {}

    @PostConstruct
    public void init() {
        System.out.println("ItemProcessor init.");
    }
    
    @Override
    public Object processItem(Object item) throws Exception {
        System.out.println("[Processor] item = " + item);
        return ((String)item).toUpperCase();
    }
}
