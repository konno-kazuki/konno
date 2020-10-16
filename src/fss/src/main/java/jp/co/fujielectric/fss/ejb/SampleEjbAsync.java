/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.co.fujielectric.fss.ejb;

import java.util.Date;
import javax.ejb.Asynchronous;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Stateless;

/**
 *
 */
@Singleton
public class SampleEjbAsync {

    /**
     * 非同期プロセス
     * 処理が非同期に実行される。
     * 本メソッドは10秒の待機時間を持つが、これを待たずに次の処理が行われる。
     */
    @Asynchronous
    public void asyncProcess() {
        System.out.println("[SampleEjbAsync]asyncProcess:before" + new Date().toString());

        try {
            Thread.sleep(10000);
        }catch(InterruptedException e){
            e.printStackTrace();
        }

        System.out.println("[SampleEjbAsync]asyncProcess:after" + new Date().toString());
    }

    /**
     * タイマープロセス
     * 設定したタイマーにそって実行される。
     * [@Schedule(hour = "*", minute = "*", second = "0")]←であれば毎時毎分0秒時に実行される。
     * 直接呼び出された場合には即時実行される。
     */
//    @Schedule(persistent = false, hour = "*", minute = "*", second = "0") 現状は一旦コメントアウト
    public void timerProcess() {
        System.out.println("[SampleEjbAsync]timerProcess:(before)" + new Date().toString());

        try {
            Thread.sleep(90000);
        }catch(InterruptedException e){
            e.printStackTrace();
        }

        System.out.println("[SampleEjbAsync]timerProcess:(after)" + new Date().toString());
    }
}
