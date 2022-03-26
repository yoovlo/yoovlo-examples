package org.yoovlo.shr.java.ex;
import org.yoovlo.shr.java.Object_g;
import org.yoovlo.shr.java.Result;
/*
object relational mapper
	a mock class to manage data access operations
*/
class Orm {
	public long id_cur;
	public Orm() {
		id_cur=1L;
	}
	public Result save(Object_g o) {
		o.id=id_cur;
		++id_cur;
		/*
		execute a request to the data service here
		*/
		Result res=new Result();
		return res;
	}
}