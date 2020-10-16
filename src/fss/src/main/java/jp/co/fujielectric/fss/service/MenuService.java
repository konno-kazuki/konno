package jp.co.fujielectric.fss.service;

import java.util.ArrayList;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.transaction.Transactional;
import jp.co.fujielectric.fss.common.AppTrace;
import jp.co.fujielectric.fss.common.SqlTrace;
import jp.co.fujielectric.fss.common.SyncDbAuto;
import jp.co.fujielectric.fss.entity.Menu;
import jp.co.fujielectric.fss.entity.UserTypePermission;

/**
 * メニューサービス
 */
@AppTrace
@SqlTrace
@SyncDbAuto
@ApplicationScoped
public class MenuService {

    @Inject
    private EntityManager em;

    @Inject
    private UserTypePermissionService userTypePermissionService;

    @Transactional
    public Menu find(Object id) {
        return em.find(Menu.class, id);
    }

    @Transactional
    public List<Menu> findAll() {
        javax.persistence.criteria.CriteriaQuery<Menu> cq = em.getCriteriaBuilder().createQuery(Menu.class);
        cq.select(cq.from(Menu.class));
        return em.createQuery(cq).getResultList();
    }

    @Transactional
    public List<Menu> findByPermission(int tabNo, String userType, String section) {
        List<Menu> listMenu = new ArrayList<>();

        Query query = em.createNamedQuery("Menu.findTabNo", Menu.class);
        query.setParameter("tabNo", tabNo);
        List<Menu> resultList = query.getResultList();

        // メニュー一覧の権限を確認。権限に合致したもののみ、targetを設定
        for (Menu menu : resultList) {
            UserTypePermission utp = userTypePermissionService.findByUnique(menu.getLink(), userType, section);
            if (utp != null) {
                menu.setTarget(utp.getLink());
                listMenu.add(menu);
            }
        }

        return listMenu;
    }

    @Transactional
    public void create(Menu entity) {
        em.persist(entity);
    }

    @Transactional
    public void edit(Menu entity) {
        em.merge(entity);
    }

    @Transactional
    public void remove(int id) {
        Menu entity = em.find(Menu.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }
}
