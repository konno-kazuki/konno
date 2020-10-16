package jp.co.fujielectric.fss.batch;

import java.io.Serializable;
import javax.batch.api.chunk.ItemReader;
import javax.enterprise.context.Dependent;
import javax.inject.Named;

@Named
@Dependent
public class TestItemReader implements ItemReader {
    // コンストラクタ（CDIのために必ず必要）
    public TestItemReader() {}

    @Override
    public void open(Serializable checkPoint) throws Exception {
        System.out.println("[Reader] open");
    }

    private int counter;

    @Override
    public Object readItem() throws Exception {
        String item;

        if (counter < 22) {
            item = String.format("item-%03d", ++counter);
        } else {
            item = null;
        }

        System.out.println("[Reader] readItem. item = " + item);

        return item;
    }

    @Override
    public void close() throws Exception {
        System.out.println("[Reader] close");
    }

    @Override
    public Serializable checkpointInfo() throws Exception {
        System.out.println("[Reader] checkpointInfo");
        return null;
    }
}
