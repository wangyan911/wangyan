package cn.edu.swu.zz.equipment;

import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@RestController
@RequestMapping("/api")
public class ManagementController {
    private final JdbcTemplate db;
    private final PasswordEncoder encoder;
    public ManagementController(JdbcTemplate db, PasswordEncoder encoder) { this.db = db; this.encoder = encoder; }

    @GetMapping("/categories") public List<Map<String,Object>> categories() { return db.queryForList("SELECT id,name,description FROM categories ORDER BY name"); }
    @PostMapping("/categories") public Map<String,Object> addCategory(@RequestBody CategoryForm f, HttpSession s) {
        require(s,"ADMIN","STAFF"); String name=clean(f.name()); if(name.isBlank()) bad("分类名称不能为空");
        db.update("INSERT INTO categories(name,description) VALUES(?,?)",name,clean(f.description()));
        return db.queryForMap("SELECT id,name,description FROM categories WHERE name=?",name);
    }
    @PutMapping("/categories/{id}") public Map<String,Object> editCategory(@PathVariable long id,@RequestBody CategoryForm f,HttpSession s) {
        require(s,"ADMIN","STAFF"); if(clean(f.name()).isBlank()) bad("分类名称不能为空");
        db.update("UPDATE categories SET name=?,description=? WHERE id=?",clean(f.name()),clean(f.description()),id);
        return db.queryForMap("SELECT id,name,description FROM categories WHERE id=?",id);
    }
    @DeleteMapping("/categories/{id}") public Map<String,String> deleteCategory(@PathVariable long id,HttpSession s) {
        require(s,"ADMIN"); db.update("UPDATE equipment SET category_id=NULL WHERE category_id=?",id); db.update("DELETE FROM categories WHERE id=?",id); return ok();
    }

    @GetMapping("/equipment") public List<Map<String,Object>> equipment(@RequestParam(defaultValue="") String q) {
        String term="%"+q.trim()+"%";
        return db.queryForList("SELECT e.id,e.code,e.name,e.category_id,COALESCE(c.name,'未分类') category,e.specification,e.total_quantity,e.available_quantity,e.location,e.notes,e.created_at FROM equipment e LEFT JOIN categories c ON c.id=e.category_id WHERE e.name LIKE ? OR e.code LIKE ? OR c.name LIKE ? ORDER BY e.name",term,term,term);
    }
    @PostMapping("/equipment") public Map<String,Object> addEquipment(@RequestBody EquipmentForm f,HttpSession s) {
        require(s,"ADMIN","STAFF"); validateEquipment(f);
        String code=clean(f.code()); if(code.isBlank()) code=InitialData.code();
        db.update("INSERT INTO equipment(code,name,category_id,specification,total_quantity,available_quantity,location,notes) VALUES(?,?,?,?,?,?,?,?)",
                code,clean(f.name()),nullableCategory(f.categoryId()),clean(f.specification()),f.quantity(),f.quantity(),clean(f.location()),clean(f.notes()));
        return equipmentByCode(code);
    }
    @PutMapping("/equipment/{id}") public Map<String,Object> editEquipment(@PathVariable long id,@RequestBody EquipmentForm f,HttpSession s) {
        require(s,"ADMIN","STAFF"); validateEquipment(f);
        Map<String,Object> old=one("SELECT total_quantity,available_quantity FROM equipment WHERE id=?",id);
        int borrowed=((Number)old.get("total_quantity")).intValue()-((Number)old.get("available_quantity")).intValue();
        if(f.quantity()<borrowed) bad("总数不能少于当前借出或维修数量："+borrowed);
        db.update("UPDATE equipment SET code=?,name=?,category_id=?,specification=?,total_quantity=?,available_quantity=?,location=?,notes=? WHERE id=?",
                clean(f.code()),clean(f.name()),nullableCategory(f.categoryId()),clean(f.specification()),f.quantity(),f.quantity()-borrowed,clean(f.location()),clean(f.notes()),id);
        return equipmentById(id);
    }
    @DeleteMapping("/equipment/{id}") public Map<String,String> deleteEquipment(@PathVariable long id,HttpSession s) {
        require(s,"ADMIN"); Integer n=db.queryForObject("SELECT COUNT(*) FROM loans WHERE equipment_id=?",Integer.class,id);
        if(n!=null&&n>0) conflict("已有借还记录，不能删除；可将器材备注为停用");
        db.update("DELETE FROM equipment WHERE id=?",id); return ok();
    }

    @GetMapping("/loans") public List<Map<String,Object>> loans(HttpSession s) {
        long uid=userId(s); String role=(String)s.getAttribute("role");
        String sql="SELECT l.id,l.equipment_id,e.name equipment_name,e.code,l.borrower_id,u.display_name borrower,l.quantity,l.purpose,l.borrowed_at,l.due_at,l.returned_at,l.status FROM loans l JOIN equipment e ON e.id=l.equipment_id JOIN users u ON u.id=l.borrower_id";
        return "BORROWER".equals(role)?db.queryForList(sql+" WHERE l.borrower_id=? ORDER BY l.id DESC",uid):db.queryForList(sql+" ORDER BY l.id DESC");
    }
    @PostMapping("/loans") @Transactional public Map<String,Object> borrow(@RequestBody BorrowForm f,HttpSession s) {
        long uid=userId(s); require(s,"ADMIN","STAFF","BORROWER");
        if(f.equipmentId()<=0||f.quantity()<=0) bad("请选择器材并填写有效数量");
        int changed=db.update("UPDATE equipment SET available_quantity=available_quantity-? WHERE id=? AND available_quantity>=?",f.quantity(),f.equipmentId(),f.quantity());
        if(changed==0) conflict("器材不存在或可借库存不足");
        db.update("INSERT INTO loans(equipment_id,borrower_id,quantity,purpose,due_at) VALUES(?,?,?,?,?)",f.equipmentId(),uid,f.quantity(),clean(f.purpose()),clean(f.dueAt()));
        return db.queryForMap("SELECT l.id,e.name equipment_name,u.display_name borrower,l.quantity,l.purpose,l.borrowed_at,l.due_at,l.status FROM loans l JOIN equipment e ON e.id=l.equipment_id JOIN users u ON u.id=l.borrower_id WHERE l.id=last_insert_rowid()");
    }
    @PostMapping("/loans/{id}/return") @Transactional public Map<String,Object> returnLoan(@PathVariable long id,HttpSession s) {
        Map<String,Object> loan=one("SELECT equipment_id,borrower_id,quantity,status FROM loans WHERE id=?",id);
        if("RETURNED".equals(loan.get("status"))) conflict("该记录已归还");
        if("BORROWER".equals(s.getAttribute("role"))&&((Number)loan.get("borrower_id")).longValue()!=userId(s)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只能归还自己的借用记录");
        db.update("UPDATE equipment SET available_quantity=available_quantity+? WHERE id=?",((Number)loan.get("quantity")).intValue(),((Number)loan.get("equipment_id")).longValue());
        db.update("UPDATE loans SET status='RETURNED',returned_at=CURRENT_TIMESTAMP WHERE id=?",id);
        return db.queryForMap("SELECT id,status,returned_at FROM loans WHERE id=?",id);
    }

    @GetMapping("/inventory") public List<Map<String,Object>> inventory(HttpSession s) { require(s,"ADMIN","STAFF"); return db.queryForList("SELECT i.id,e.code,e.name,i.expected_quantity,i.actual_quantity,i.actual_quantity-i.expected_quantity difference,i.note,u.display_name checker,i.checked_at FROM inventory_checks i JOIN equipment e ON e.id=i.equipment_id JOIN users u ON u.id=i.checker_id ORDER BY i.id DESC LIMIT 300"); }
    @PostMapping("/inventory") @Transactional public Map<String,Object> inventoryCheck(@RequestBody InventoryForm f,HttpSession s) {
        require(s,"ADMIN","STAFF"); if(f.equipmentId()<=0||f.actualQuantity()<0) bad("盘点数量无效");
        Map<String,Object> e=one("SELECT total_quantity,available_quantity FROM equipment WHERE id=?",f.equipmentId());
        int total=((Number)e.get("total_quantity")).intValue(), available=((Number)e.get("available_quantity")).intValue();
        int borrowed=total-available; if(f.actualQuantity()<borrowed) conflict("实盘数不能小于已借出/维修数量："+borrowed);
        db.update("INSERT INTO inventory_checks(equipment_id,checker_id,expected_quantity,actual_quantity,note) VALUES(?,?,?,?,?)",f.equipmentId(),userId(s),total,f.actualQuantity(),clean(f.note()));
        db.update("UPDATE equipment SET total_quantity=?,available_quantity=? WHERE id=?",f.actualQuantity(),f.actualQuantity()-borrowed,f.equipmentId());
        return Map.of("message","盘点完成","difference",f.actualQuantity()-total);
    }

    @GetMapping("/repairs") public List<Map<String,Object>> repairs(HttpSession s) { require(s,"ADMIN","STAFF"); return db.queryForList("SELECT r.id,r.equipment_id,e.name equipment_name,e.code,r.quantity,r.issue,r.status,r.created_at,r.resolved_at,u.display_name reporter FROM repairs r JOIN equipment e ON e.id=r.equipment_id JOIN users u ON u.id=r.reporter_id ORDER BY r.id DESC"); }
    @PostMapping("/repairs") @Transactional public Map<String,Object> reportRepair(@RequestBody RepairForm f,HttpSession s) {
        require(s,"ADMIN","STAFF"); if(f.equipmentId()<=0||f.quantity()<=0||clean(f.issue()).isBlank()) bad("请填写器材、数量和故障描述");
        int changed=db.update("UPDATE equipment SET available_quantity=available_quantity-? WHERE id=? AND available_quantity>=?",f.quantity(),f.equipmentId(),f.quantity());
        if(changed==0) conflict("可用库存不足或器材不存在");
        db.update("INSERT INTO repairs(equipment_id,reporter_id,quantity,issue) VALUES(?,?,?,?)",f.equipmentId(),userId(s),f.quantity(),clean(f.issue()));
        return Map.of("message","维修记录已登记");
    }
    @PostMapping("/repairs/{id}/resolve") @Transactional public Map<String,String> resolveRepair(@PathVariable long id,HttpSession s) {
        require(s,"ADMIN","STAFF"); Map<String,Object> r=one("SELECT equipment_id,quantity,status FROM repairs WHERE id=?",id);
        if("RESOLVED".equals(r.get("status"))) conflict("此维修记录已处理");
        db.update("UPDATE equipment SET available_quantity=available_quantity+? WHERE id=?",((Number)r.get("quantity")).intValue(),((Number)r.get("equipment_id")).longValue());
        db.update("UPDATE repairs SET status='RESOLVED',resolved_at=CURRENT_TIMESTAMP WHERE id=?",id); return ok();
    }

    @GetMapping("/users") public List<Map<String,Object>> users(HttpSession s) { require(s,"ADMIN"); return db.queryForList("SELECT id,username,display_name,role,enabled,created_at FROM users ORDER BY id"); }
    @PostMapping("/users") public Map<String,Object> addUser(@RequestBody UserForm f,HttpSession s) {
        require(s,"ADMIN"); validateUser(f); db.update("INSERT INTO users(username,display_name,password_hash,role) VALUES(?,?,?,?)",clean(f.username()),clean(f.displayName()),encoder.encode(f.password()),f.role());
        return db.queryForMap("SELECT id,username,display_name,role,enabled FROM users WHERE username=?",clean(f.username()));
    }
    @PutMapping("/users/{id}") public Map<String,Object> updateUser(@PathVariable long id,@RequestBody UserForm f,HttpSession s) {
        require(s,"ADMIN"); if(!List.of("ADMIN","STAFF","BORROWER").contains(f.role())) bad("角色无效");
        if(id==userId(s)&&!f.enabled()) conflict("不能停用当前登录账号");
        db.update("UPDATE users SET display_name=?,role=?,enabled=? WHERE id=?",clean(f.displayName()),f.role(),f.enabled()?1:0,id);
        if(f.password()!=null&&!f.password().isBlank()) db.update("UPDATE users SET password_hash=? WHERE id=?",encoder.encode(f.password()),id);
        return db.queryForMap("SELECT id,username,display_name,role,enabled FROM users WHERE id=?",id);
    }

    @GetMapping("/reports/summary") public Map<String,Object> summary(HttpSession s) { require(s,"ADMIN","STAFF");
        return Map.of("equipmentKinds",db.queryForObject("SELECT COUNT(*) FROM equipment",Integer.class),"totalQuantity",db.queryForObject("SELECT COALESCE(SUM(total_quantity),0) FROM equipment",Integer.class),"availableQuantity",db.queryForObject("SELECT COALESCE(SUM(available_quantity),0) FROM equipment",Integer.class),"activeLoans",db.queryForObject("SELECT COUNT(*) FROM loans WHERE status='BORROWED'",Integer.class),"openRepairs",db.queryForObject("SELECT COUNT(*) FROM repairs WHERE status='OPEN'",Integer.class));
    }
    @GetMapping("/reports/equipment.csv") public ResponseEntity<byte[]> exportEquipment(HttpSession s) { require(s,"ADMIN","STAFF");
        List<Map<String,Object>> rows=equipment(""); StringBuilder csv=new StringBuilder("\uFEFF编号,器材名称,分类,规格,总数,可借数量,存放位置,备注\r\n");
        for(var r:rows) csv.append(cell(r.get("code"))).append(',').append(cell(r.get("name"))).append(',').append(cell(r.get("category"))).append(',').append(cell(r.get("specification"))).append(',').append(r.get("total_quantity")).append(',').append(r.get("available_quantity")).append(',').append(cell(r.get("location"))).append(',').append(cell(r.get("notes"))).append("\r\n");
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=equipment.csv").contentType(new MediaType("text","csv",StandardCharsets.UTF_8)).body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }
    private Map<String,Object> equipmentById(long id){return db.queryForMap("SELECT e.*,COALESCE(c.name,'未分类') category FROM equipment e LEFT JOIN categories c ON c.id=e.category_id WHERE e.id=?",id);}
    private Map<String,Object> equipmentByCode(String code){return db.queryForMap("SELECT e.*,COALESCE(c.name,'未分类') category FROM equipment e LEFT JOIN categories c ON c.id=e.category_id WHERE e.code=?",code);}
    private Object nullableCategory(Long id){return id==null||id<=0?null:id;}
    private Map<String,Object> one(String sql,Object...args){try{return db.queryForMap(sql,args);}catch(org.springframework.dao.EmptyResultDataAccessException e){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"记录不存在");}}
    private void validateEquipment(EquipmentForm f){if(clean(f.name()).isBlank()||f.quantity()<0)bad("器材名称必填，数量不能小于0");}
    private void validateUser(UserForm f){if(clean(f.username()).isBlank()||clean(f.displayName()).isBlank()||f.password()==null||f.password().length()<8||!List.of("ADMIN","STAFF","BORROWER").contains(f.role()))bad("账号、姓名必填；初始密码至少8位；角色无效");}
    private static String clean(String s){return s==null?"":s.trim();}
    private static String cell(Object o){String v=o==null?"":o.toString();return "\""+v.replace("\"","\"\"")+"\"";}
    private static long userId(HttpSession s){return ((Number)s.getAttribute("userId")).longValue();}
    private static void require(HttpSession s,String...roles){Object r=s.getAttribute("role");for(String role:roles)if(role.equals(r))return;throw new ResponseStatusException(HttpStatus.FORBIDDEN,"权限不足");}
    private static void bad(String m){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
    private static void conflict(String m){throw new ResponseStatusException(HttpStatus.CONFLICT,m);}
    private static Map<String,String> ok(){return Map.of("message","操作成功");}
    public record CategoryForm(String name,String description){}
    public record EquipmentForm(String code,String name,Long categoryId,String specification,int quantity,String location,String notes){}
    public record BorrowForm(long equipmentId,int quantity,String purpose,String dueAt){}
    public record InventoryForm(long equipmentId,int actualQuantity,String note){}
    public record RepairForm(long equipmentId,int quantity,String issue){}
    public record UserForm(String username,String displayName,String password,String role,boolean enabled){}
}
