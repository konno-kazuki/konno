package jp.co.fujielectric.fss.data;

/**
 * 2値をまとめて扱うクラス
 * @param <T1> 値1の型
 * @param <T2> 値2の型
 */
public class Tuple<T1, T2> {
    private T1 value1;
    private T2 value2;
    
    public Tuple(T1 value1, T2 value2) {
        this.value1 = value1;
        this.value2 = value2;
    }
    
    public void setValue1(T1 value1) {
        this.value1 = value1;
    }
    
    public void setValue2(T2 value2) {
        this.value2 = value2;
    }
    
    public T1 getValue1() {
        return value1;
    }
    
    public T2 getValue2() {
        return value2;
    }
}
