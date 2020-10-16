package jp.co.fujielectric.fss.service;

import java.util.ArrayList;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.common.SyncDbAuto;
import jp.co.fujielectric.fss.data.SampleResult;
import jp.co.fujielectric.fss.data.Tuple;
import jp.co.fujielectric.fss.entity.Sample;
import jp.co.fujielectric.fss.entity.SampleContent;
import jp.co.fujielectric.fss.entity.SampleSubject;
import jp.co.fujielectric.fss.util.IdUtil;

/**
 * サンプルサービス
 */
@AppTrace
@SqlTrace
@SyncDbAuto
@ApplicationScoped
public class SampleService {

    @Inject
    private EntityManager em;

    @Transactional
    public void create(Sample entity) {
        em.persist(entity);
    }

    @Transactional
    public void edit(Sample entity) {
        em.merge(entity);
    }

    @Transactional
    public void remove(Sample entity) {
        em.remove(entity);
    }

    @Transactional
    public void insertSample(int id, String name, String type) {
        Sample sample = new Sample();
        sample.setId(id);
        sample.setName(name);
        sample.setType(type);
        em.persist(sample);
    }

    @Transactional
    public void updateSample(int id, String name, String type) {
        Sample sample = em.find(Sample.class, id);
        if (sample != null) {
            sample.setName(name);
            sample.setType(type);
            em.merge(sample);
        }
    }

    @Transactional
    public void deleteSample(int id) {
        Sample sample = em.find(Sample.class, id);
        if (sample != null) {
            em.remove(sample);
        }
    }

    @Transactional
    public Sample findSample(int id) {
        Sample sample = em.find(Sample.class, id);
        if (sample == null) {
            sample = new Sample();
        }
        return sample;
    }

    @Transactional
    public Sample findSampleName(String name) {
        Sample sample = new Sample();

        // NamedQueryを利用して名称からエンティティを取得
        Query query = em.createNamedQuery("Sample.findName", Sample.class);
        query.setParameter("name", name);
        List<Sample> samples = query.getResultList();

        // 結果リストから単体を取得
        if (samples != null && samples.isEmpty() == false) {
            sample = samples.iterator().next();
        }

        return sample;
    }

    @Transactional
    public SampleSubject findSampleSubject(String id) {
        SampleSubject sampleSubject = em.find(SampleSubject.class, id);

        if (sampleSubject != null) {
            System.out.println("sampleSubject(id):" + sampleSubject.getId());
            System.out.println("sampleSubject(subject):" + sampleSubject.getSubject());
            for (SampleContent sampleContent : sampleSubject.getSampleContents()) {
                System.out.println("sampleContent(id):" + sampleContent.getId());
                System.out.println("sampleContent(content):" + sampleContent.getContent());
            }
        }
        return sampleSubject;
    }

    @Transactional
    public String createSampleSubject() {
        // SampleSubjectの作成
        SampleSubject ss = new SampleSubject();
        ss.setId(IdUtil.createUUID());
        ss.setSubject("Subject");

        // SampleContentの格納
        for (int i = 0; i < 10; i++) {
            SampleContent sc = new SampleContent();
            sc.setId(IdUtil.createUUID());
            sc.setContentNo(i);
            sc.setContent("Content" + i);
            sc.setSampleSubject(ss);
            ss.getSampleContents().add(sc);
        }
        em.persist(ss);

        return ss.getId();
    }

    public List<Sample> sampleQuery(String name, String type) {
        if(name == null && type == null) {
            return null;
        }

        if(false) {
            // JPQLによる実装（動的制御がかっこ悪い）
            String jpql = "select s from Sample s ";
            jpql += "where ";

            List<Tuple<String, Object>> param = new ArrayList<>();

            if(name != null) {
                jpql += "name like :name ";
                param.add(new Tuple<>("name", "%"+name+"%"));
            }
            if(param.size() > 0) { jpql += "and "; }
            if(type != null) {
                jpql += "type like :type ";
                param.add(new Tuple<>("type", "%"+type+"%"));
            }

            Query query = em.createQuery(jpql, Sample.class);

            for(Tuple<String, Object> p : param) {
                query.setParameter(p.getValue1(), p.getValue2());
            }

            return query.getResultList();
        } else {
            // CriteriaBuilderによる実装（記述がわかりづらい）
            CriteriaBuilder builder = em.getCriteriaBuilder();
            CriteriaQuery<Sample> query = builder.createQuery(Sample.class);

            Root<Sample> root = query.from(Sample.class);

            query.select(root);
            List<Predicate> creteria = new ArrayList<Predicate>();

            if(name != null) {
                creteria.add(builder.like(root.get("name"), "%"+name+"%"));
            }
            if(type != null) {
                creteria.add(builder.like(root.get("type"), "%"+type+"%"));
            }

            if(creteria.size() > 0) {
                query.where(builder.and(creteria.toArray(new Predicate[]{})));
            }

            return em.createQuery(query).getResultList();
        }
    }

    public List<SampleResult> sampleJoinQuery(String subject, String content) {

        if(false) {
            // JPQLによる実装（動的制御がかっこ悪くてミスしやすい）
            String jpql = "select new jp.co.fujielectric.fss.data.SampleResult(ss.id, ss.subject, sc.content, sc.contentNo) "
                    + "from SampleSubject ss left join ss.SampleContents sc ";
            jpql += "where ";

            List<Tuple<String, Object>> param = new ArrayList<>();

            if(subject != null) {
                jpql += "ss.subject like :subject ";
                param.add(new Tuple<>("subject", "%"+subject+"%"));
            }
            if(param.size() > 0) { jpql += "and "; }
            if(content != null) {
                jpql += "sc.content like :content ";
                param.add(new Tuple<>("content", "%"+content+"%"));
            }

            Query query = em.createQuery(jpql, SampleResult.class);

            for(Tuple<String, Object> p : param) {
                query.setParameter(p.getValue1(), p.getValue2());
            }

            return query.getResultList();
        } else {
            // CriteriaBuilderによる実装（記述がわかりづらくてめんどくさい）
            CriteriaBuilder builder = em.getCriteriaBuilder();
            CriteriaQuery<SampleResult> query = builder.createQuery(SampleResult.class);

            Root<SampleSubject> root = query.from(SampleSubject.class);

            Join<SampleSubject, SampleContent> join = root.join("SampleContents");

            query.select(builder.construct(SampleResult.class, root.get("id"), root.get("subject"), join.get("content"), join.get("contentNo") ));
            List<Predicate> predicate = new ArrayList<Predicate>();

            if(subject != null) {
                predicate.add(builder.like(root.get("subject"), "%"+subject+"%"));
            }
            if(content != null) {
                predicate.add(builder.like(join.get("content"), "%"+content+"%"));
            }

            if(predicate.size() > 0) {
                query.where(builder.and(predicate.toArray(new Predicate[]{})));
            }

            return em.createQuery(query).getResultList();
        }
    }
}
