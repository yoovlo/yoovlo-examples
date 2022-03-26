package org.yoovlo.shr.java.ex;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.yoovlo.shr.java.req.Req;
import org.yoovlo.shr.java.req.Res;
import org.yoovlo.shr.java.req.Req_info;
import org.yoovlo.shr.java.T;
import org.yoovlo.shr.java.Globals;
import org.yoovlo.shr.java.Result;
import org.yoovlo.shr.java.Ox;
import org.yoovlo.shr.java.Utils;
import javax.annotation.PostConstruct;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
/*
example service
*/
@RestController
@RequestMapping("ex")
public class Ex_svc {
	//state
	public int st;
	public Orm orm;
	@PostConstruct
	public void init() {
		T.t("Ex_svc init");
		/*
		this puts this service into the global svcs map
			this classes methods that conform to the signature function(Map<String,Object> b,Req_info ri)
				will be able to be called by Req_handler
			we register this service so that we can call it's functions purchase_crt_proc,log throug Req_handler
		*/
		Globals.svcs_m.put("Ex",Ex_svc.class);
		st=1;
		//mock object relational mapper
		orm=new Orm();
	}
	/*
	call this function with: http://localhost:1356/ex/purchase_crt?app_id=1&tnt_id=1&u_id=1&u_sh=a&store_id=1&item_id=1
	purchase create
		this is the introductory example to illustrate the purpose of Yoovlo as described here:
			https://yoovlo.org/yoovlo/docs/workflows
		app_id: app id
		tnt_id: tenant id
		u_id: user id
		u_sh: user session hash
		store_id: store id
		item_id: item id
	*/
	@RequestMapping(value="/purchase_crt",produces="application/json",method={RequestMethod.POST,RequestMethod.GET})
	public Result purchase_crt(
		@RequestParam Long app_id,
		@RequestParam Long tnt_id,
		@RequestParam Long u_id,
		@RequestParam String u_sh,
		@RequestParam Long store_id,
		@RequestParam Long item_id,
		HttpServletRequest request,
		HttpServletResponse response) {
		Result res=null;
		res=authn(app_id,tnt_id,u_id,u_sh);
		if(!res.success) {
			return res;
		}
		User u=(User)res.info.get("u");
		res=authz(u,"purchase_crt","Store",store_id,"Item",item_id);
		if(!res.success) {
			return res;
		}
		/*
		here we kick off the business logic by creating an Ox object
			Ox stands for Object_x, it is a small utility object that can be used to manage state for a process
				the x stands for cross, as in cross platform, cross utilty. really, it's just a random letter for this utility class to maintain state, counting, schemaless data, etc
				or Ox as in the animal: an generic object useful for processing
			we then proceed with creating a req to the service action that will perform the business logic
				and performing Utils.task_crt(req) to push the req execution into the task queue
			the client can query the status of the process from the provided Ox Id or we can push status to the client on process completion
		*/
		Ox p=new Ox();//p for process
		orm.save(p);

		Req req=new Req();
		req.exec_type="jvm";
		req.s="Ex";
		req.a="purchase_crt_proc";
		req.b_init();
		HashMap<String,Object> b=req.b;
		b.put("app_id",app_id);
		b.put("tnt_id",tnt_id);
		b.put("u_id",u_id);
		b.put("u_sh",u_sh);
		b.put("p_id",p.id);
		b.put("store_id",store_id);
		b.put("item_id",item_id);
		Utils.task_crt(req);

		res=new Result();
		res.info_init();
		res.info.put("p_id",p.id);
		return res;
	}
	/*
	purchase create process
		in a production deployment action needs to be placed into an environment where it cannot be called directly from the outside
			it would be either guarded by a internal authorization function, ie an internal key
			or it should be compiled into a internal binary that is then only deployed into an internal network scope
		notice the signature this function: HashMap<String,Object> b,Req_info ri
			we are accepting a body in the b variable, it is generally serialized as a POST body
			and we have a Req_info object that is built by the Req_svc and provided to the Req_handler
			this function signature is how we implement Req handlers in Yoovlo
				our Req tasks will be executed by Yoovlo at the Req_svc
					the Req_svc will accept a POST request with the body and execute the Req through the Req_handler
					we specified that the req.exec_type is jvm:
						Yoovlo will execute this function directly in the jvm of the Yoovlo node
	*/
	public void purchase_crt_proc(HashMap<String,Object> b,Req_info ri) {
		/*
		this is the action that performs the business logic process of purchase create
			it may for example 
				first kick off a task to do fraud verification
				then contact the payment gateway etc
				for each part of the process, we may additionaly want to execute a task to the Log.log action
			when all of the tasks and/or workflows are completed, it will modify the state of the process Ox object
				and potentially notify the client that the process has completed if we are operating a push gateway
					we can store the gateway that the client is connected to in the Ox
				otherwise, the client will poll for the status of the Ox object
		*/
		T.t("purchase_crt_proc");
		Result res=new Result();
		ri.r(res);
	}
	/*
	this is the authentication function
		every request needs to authenticate the Identity of the operating User
			while the form of how Identity information is passed has changed over the years from basic HTTP auth
			to JWT, device information and so forth
				the intended logic on the server remains the same
					we need to lookup the user object in a database and confirm that the provided session is still valid
					in order to locate the database (or database shard) in a tenanted architecture
						we need the app_id and tenant_id
		u_sh means user session hash
			the explanation of why "hash" is a better name for variables of this type, than "key" in other programming libraries
				can be found here: 
	*/
	public Result authn(Long app_id,Long tnt_id,Long u_id, String u_sh) {
		Map<String,Object> info=new HashMap<String,Object>();
		info.put("app_id",app_id);
		info.put("tnt_id",tnt_id);
		info.put("u_id",u_id);
		info.put("u_sh",u_sh);
		/*
		this creates an asynchronous distributed task that will eventually be executed on one of the Yoovlo worker nodes
			we do not have to rely on our local thread pool to perform this work
			we do not have to wait for the task to complete
		*/
		log_task_crt("authn",info);

		/*
		perform authorization against an internal database or an Auth service
			at this point here we would write the code that verifies the Identity provided
				as this is an example we always return successfully
				we can use a service like Tenance to easily perform Authentication and Authorization using the principles specified here
		*/
		Result res=new Result();
		res.info_init();
		User u=new User();
		u.id=u_id;
		u.sh=u_sh;
		res.info.put("u",u);
		return res;
	}
	/*
	this is the authorization function
		authorization requires establishing relationships between objects based on the intended Operation
		we observe that the most common intended behavior we are trying to model is the following:
			our logged in User is performing an operation on a Resource that belongs to a different User
				as such, the business logic that is being performed is mostly from the perspective of the different User
					as such, we can refer to such a User as the Target
					the associated Resource that we are interested in, we refer to as Foreign
						our business logic may be written from the perspective of Target to Foreign
							namely by examining the established Relations between Target and Foreign
				because this type of intended functionality is common to a large number of requests
					we can write code that is generic independent of the Types of the Target and the Foreign
						ie, we can write code that is generic and independent to the Target Type and Foreign Type
							as such, we expect as parameters to identify Target: t_type, t_id
							likewise, we expect as parameters to identify Foreign: f_type, f_id
	*/
	public Result authz(User op_user,String op,String t_type,Long t_id,String f_type,Long f_id) {
		/*
		lookup Target, Foreign
			examine if there are sufficient relationships required by the Operation (op)
				between:
					op_user and target
					op_user and foreign
					target and foreign
		*/
		Map<String,Object> info=new HashMap<String,Object>();
		info.put("op_u_id",op_user.id);
		info.put("op",op);
		info.put("t_type",t_type);
		info.put("t_id",t_id);
		info.put("f_type",f_type);
		info.put("f_id",f_id);
		log_task_crt("authz",info);

		Result res=new Result();
		res.info_init();
		Store t=new Store();
		t.id=t_id;
		res.info.put("t",t);
		Item f=new Item();
		f.id=f_id;
		res.info.put("f",f);
		return res;
	}
	/*
	log task create
		this function produces a Req task into our task event queue
			the task is consumed and processed by a Yoovlo node configured to consume events from the task event queue
			the Yoovlo node determines that the exec_type of the Req is rs (request server) as specified by req.exec_type="rs";
				it executes a request to the request server url
					the request server determines that the request is aimed at the Log service, with the action "log"
						as specified by the lines:
							req.s="Log";
							req.a="log";
						the implementation of the log action further produces events or performs syncronous requests to third party logging services
							our internal analytics infrastructure can aggregate events of this type (Req.Log.log) to provide observability
		the point of this flow of events, is that we have asynchronously disintermediated the execution of all the cascading analytics requests, from the business logic
			our Ex_svc_spring.purchase_crt is not waiting for the return of http requests to third party services or our logging systems to complete their processing
				it is simply sending a single asynchronous Req.Log.log event that will cascade further events as necessary
				it can proceed to performing authn,authz,starting business logic and finally responding immediately to the client
					without having to wait for the entire cascade of blocking operations
				essentially, we are performing asynchronous execution not at the local thread pool level, but at the distributed compute infrastructure level
	*/
	public Result log_task_crt(String msg,Map<String,Object> info) {
		Req req=new Req();
		req.exec_type="jvm";
		req.s="Ex";
		req.a="log";
		req.b_init();
		HashMap<String,Object> b=req.b;
		b.put("m",msg);
		b.put("i",info);
		Result task_res=Utils.task_crt(req);
		return task_res;
	}
	public void log(HashMap<String,Object> b,Req_info ri) {
		String m=(String)b.get("m");
		HashMap<String,Object> i=(HashMap<String,Object>)b.get("i");
		T.t("log m:"+m);
		Result res=new Result();
		ri.r(res);
	}
}
