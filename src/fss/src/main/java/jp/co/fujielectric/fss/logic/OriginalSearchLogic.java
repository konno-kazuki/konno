package jp.co.fujielectric.fss.logic;

import java.util.ArrayList;
import java.util.List;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import jp.co.fujielectric.fss.data.OriginalSearchForm;
import jp.co.fujielectric.fss.data.OriginalSearchResult;
import jp.co.fujielectric.fss.entity.ReceiveFile;
import jp.co.fujielectric.fss.entity.ReceiveFile_;
import jp.co.fujielectric.fss.entity.ReceiveInfo;
import jp.co.fujielectric.fss.entity.ReceiveInfo_;
import jp.co.fujielectric.fss.entity.SendInfo;
import jp.co.fujielectric.fss.entity.SendInfo_;
import org.apache.logging.log4j.Logger;

/**
 * メール原本検索ロジック
 */
@RequestScoped
public class OriginalSearchLogic {
    @Inject
    private Logger LOG;

    @Inject
    private EntityManager em;

    /**
     * メール原本検索処理
     * @return
     */
    public List<OriginalSearchResult> searchOriginal(List<OriginalSearchForm> formList) {
        // CriteriaBuilderによる実装
        CriteriaBuilder builder = em.getCriteriaBuilder();
        // 検索結果はリザルト用クラスへ格納
        CriteriaQuery<OriginalSearchResult> query = builder.createQuery(OriginalSearchResult.class);

        // SendInfoとReceiveInfoはリレーションを持っていないため、後ほどwhereで結合
        // ReceiveInfoとReceiveFileはFileを持っていない可能性があるため、外部結合
        Root<SendInfo> sendinfo = query.from(SendInfo.class);
//        Join<SendInfo, ReceiveInfo> receiveinfo = sendinfo.join--リレーションを持っていないため、join出来ない
        Root<ReceiveInfo> receiveinfo = query.from(ReceiveInfo.class);
        Join<ReceiveInfo, ReceiveFile> receivefile = receiveinfo.join(ReceiveInfo_.receiveFiles, JoinType.LEFT);

        // SELECTを定義（DISTINCT有り）
        query.select(builder.construct(OriginalSearchResult.class,
                sendinfo.get(SendInfo_.id),
                sendinfo.get(SendInfo_.sendMailAddress),
                receiveinfo.get(ReceiveInfo_.receiveMailAddress),
                sendinfo.get(SendInfo_.sendTime),
                sendinfo.get(SendInfo_.expirationTime),
                sendinfo.get(SendInfo_.subject),
                sendinfo.get(SendInfo_.content),
                sendinfo.get(SendInfo_.procDate),
                builder.count(receivefile.get(ReceiveFile_.id)),
                builder.sum(receivefile.get(ReceiveFile_.fileSize))
                ));
        query.distinct(true);

        // GROUPを定義（集約関数以外のSELECT対象）
        query.groupBy(
                sendinfo.get(SendInfo_.id),
                sendinfo.get(SendInfo_.sendMailAddress),
                receiveinfo.get(ReceiveInfo_.receiveMailAddress),
                sendinfo.get(SendInfo_.sendTime),
                sendinfo.get(SendInfo_.expirationTime),
                sendinfo.get(SendInfo_.subject),
                sendinfo.get(SendInfo_.content),
                sendinfo.get(SendInfo_.procDate));
        // ORDERを定義（時間降順→ID順）
        query.orderBy(
                builder.desc(sendinfo.get(SendInfo_.sendTime)),
                builder.asc(sendinfo.get(SendInfo_.sendMailAddress)),
                builder.asc(receiveinfo.get(ReceiveInfo_.receiveMailAddress)));

        // 結合条件、必須条件は先に定義し、最後に統合（その他と必ずand結合させるため）
        Predicate predicate = builder.and(new Predicate[]{
                builder.equal(sendinfo.get(SendInfo_.id), receiveinfo.get(ReceiveInfo_.sendInfoId)),    // SendInfo-ReceiveInfo結合条件
                builder.isTrue(sendinfo.get(SendInfo_.attachmentMailFlg))                               // メールのみを指定
                });

        // その他の抽出条件を定義（画面指定による）
        Predicate predicateOption = null;
//        // 日付を検索（サンプル）
//        predicateOption = builder.like(builder.function("to_char", String.class, sendinfo.get(SendInfo_.sendTime), builder.literal("YYYY-MM-DD HH24:MI:SS")), "%2017-04-21%");
//        // 本文を検索（サンプル）
//        predicateOption = builder.like(sendinfo.get(SendInfo_.content), "%無害化%");
//        // ファイル名を検索（サンプル）
//        predicateOption = builder.like(receivefile.get(ReceiveFile_.fileName), "%サンプル%");

        {
            List<Predicate> predicateSectionList = new ArrayList<>();
            Predicate predicateSection = null;
            for(OriginalSearchForm form : formList){
                Predicate p = null;
                switch(form.getSearchColumn()) {
                    case sender:
                        p = builder.like(sendinfo.get(SendInfo_.sendMailAddress), createWildcardWord(form.getWord(), true, escapeChar), escapeChar);
                        break;
                    case receiver:
                        p = builder.like(receiveinfo.get(ReceiveInfo_.receiveMailAddress), createWildcardWord(form.getWord(), true, escapeChar), escapeChar);
                        break;
                    case time:
                        p = builder.like(builder.function("to_char", String.class, sendinfo.get(SendInfo_.sendTime), builder.literal("YYYY-MM-DD HH24:MI:SS")), createWildcardWord(form.getWord(), true, escapeChar), escapeChar);
                        break;
                    case subject:
                        p = builder.like(sendinfo.get(SendInfo_.subject), createWildcardWord(form.getWord(), true, escapeChar), escapeChar);
                        break;
                    case content:
                        p = builder.like(sendinfo.get(SendInfo_.content), createWildcardWord(form.getWord(), true, escapeChar), escapeChar);
                        break;
                    case filename:
                        p = builder.like(receivefile.get(ReceiveFile_.fileName), createWildcardWord(form.getWord(), true, escapeChar), escapeChar);
                        break;
                    case error:
                        p = builder.and(builder.isNull(sendinfo.get(SendInfo_.subject)), builder.isNull(sendinfo.get(SendInfo_.toAddress)));
                        break;
                    default:
                }

                if(p != null) {
                    if(predicateSection == null) {
                        predicateSection = p;
                    }else switch(form.getSearchOperate()) {
                        case and:
                            predicateSection = builder.and(predicateSection, p);
                            break;
                        case or:
                            predicateSectionList.add(predicateSection);
                            predicateSection = p;
                            break;
                        default:
                    }
                }
            }
            predicateSectionList.add(predicateSection);

            // PredicateListをORで結合
            predicateOption = builder.or(predicateSectionList.toArray(new Predicate[]{}));
        }

        // 必須条件とその他条件を統合し、WHEREを定義
        if(predicateOption != null) {
            predicate = builder.and(predicate, predicateOption);
        }
        query.where(predicate);

        // SQLの実行及び結果の返却
        TypedQuery<OriginalSearchResult> q = em.createQuery(query);
        return q.getResultList();
    }

    private final char escapeChar = '\\';

    private String createWildcardWord(String str, boolean both, char escapeChar) {
        return createWildcardWord(str, both, both, escapeChar);
    }

    private String createWildcardWord(String str, boolean before, boolean after, char escapeChar) {
        if(str == null) {return null;}

        // replaceが遅いが、利便性を重視
        StringBuilder sb = new StringBuilder(str
                .replace(String.valueOf(escapeChar), String.valueOf(new char[]{escapeChar, escapeChar}))
                .replace("%", escapeChar + "%")
                .replace("_", escapeChar + "_")
        );

        if(before) { sb.insert(0, '%'); }
        if(after)  { sb.append('%'); }

        return sb.toString();
    }
}
