require 'lib/lang/parse/local_tw.rb'
require 'lib/lang/parse/tree_walker.rb'

require 'lib/lang/parse/schema.rb'

require 'lib/types/table/object_table.rb'
require 'lib/lang/plan/predicate.rb'
#require 'lib/lang/compiler.rb'
require 'lib/lang/plan/program.rb'
require 'lib/lang/plan/rule.rb'
require 'lib/types/table/basic_table.rb'
require 'lib/types/table/catalog.rb'
require "lib/types/operator/scan_join"
require "lib/lang/plan/value.rb"
require "lib/lang/plan/arbitrary_expression.rb"
require "lib/types/function/aggregate_fn.rb"


class OverlogPlanner
	def predoftable(table)
	        schema = table.schema_of
	        p = Predicate.new(false,table.name,table,schema.variables)
		p.set("global","r1",1)
		return p
	end


	def get_scope(pred)
		scopeName = pred.to_s.split("::")
		scope = nil
		tname = pred
		unless (scopeName[1].nil?) then
			scope = scopeName[0]
			tname = scopeName[1]
		end
		return [scope,tname]
	end

	def program
		return @program
	end

	def initialize(utterance,rules,terms,preds,pexps,exps,facts,tables,columns,indices,programs,assigns,selects)
                @rules = rules
                @terms = terms
                @preds = preds
                @pexpr = pexps
                @expr = exps
                @facts = facts
                @tables = tables
                @columns = columns
                @indices = indices
                @programs = programs
		@assigns = assigns
		@selects = selects

			
		compiler = OverlogCompiler.new(@rules,@terms,@preds,@pexpr,@expr,@facts,@tables,@columns,@indices,@programs,@assigns,@selects)
                #compiler.verbose = 'y'
                compiler.parse(utterance)

                compiler.analyze
	
		# now our tables are populated.

		@program = plan_program
	end

	def plan_program
		ts = TupleSet.new("prog",*@programs.tuples)

		# ts points to my programs tuples.  for now, I'm going to assume there's only ever one row in here.
		program = ts.tups[0]
		@progname = program.value("program_name")
		return Program.new(@progname,"your mother")
	end

	def plan
		plan_materializations 
		plan_rules
		plan_facts
		@program.plan

		return @program
	end

	def plan_facts
		f_cols = predoftable(@expr)
		
		@facts.tuples.each do |fact|
			#puts fact
			tuple = get_vars(fact)
			tuple.each do |t|
			#	print "\t#{t}\n"
			end
			#print "-----\n"
		end
	end
	def plan_materializations
		@tables.tuples.each do |table| 
			p_cols = predoftable(@columns)

			scols = ScanJoin.new(p_cols,table.schema)
			rescols = scols.evaluate(TupleSet.new("cols",table))

			cols = Array.new
			# SORT!
			rescols.order_by("col_pos") do |col|
				cols << col.value("datatype")
			end
			
			p_indx = predoftable(@indices)
			sindx = ScanJoin.new(p_indx,table.schema)
			resindx = sindx.evaluate(TupleSet.new("cols",table))
		
			indxs = Array.new	
			resindx.order_by("col_pos") do |col|
				indxs << col.value("col_pos")
			end 		

			typestr = '[' + cols.join(",") + ']'
			indxstr = 'Key.new(' + indxs.join(",") + ')'


			typething = eval(typestr)
			indxthing = eval(indxstr)

			(scope,tname) = get_scope(table.value("tablename"))

			#nnnprint "scope #{scope}, tname #{tname}\n"
			
			table = BasicTable.new(TableName.new(scope,tname),Table::INFINITY, Table::INFINITY,indxthing,typething)
			@program.definition(table)			
		end
	end

	def plan_rules

		p_rule = predoftable(@rules)
	
		sj = ScanJoin.new(p_rule,@programs.schema_of)
		ts = TupleSet.new("prog",*@programs.tuples)
		# save res as a member variable: I'll want to reuse it.
		res = sj.evaluate(ts)

		# need to put an ordering over the rules!
		res.tups.each do |rule|
		
			p_term = predoftable(@terms)
			sterm = ScanJoin.new(p_term,rule.schema)
			resterm = sterm.evaluate(TupleSet.new("rule",rule))
			
			rulename = rule.value("rulename")
			body = plan_preds(resterm,rulename)
		##	body = plan_whatever(resterm,rulename,@preds,"pred_pos",pred_block)


			head = body.shift
			assigns = plan_assignments(resterm,rulename)	
			assigns.each do |a|
				body << a	
			end
			selects = plan_selections(resterm,rulename)
			selects.each do |s|
				body << s
			end
		
			# location?  extract rulename!  isPublic, isDelete
			#d = rule.value("delete").eql?("1")
			rule = Rule.new(1,rulename,true,false,head,body)
			rule.set(@progname)
		end
	end

	def get_vars(it)
			p_expr = predoftable(@expr)
			# performance hurts!  these need to be index joins
			sexpr = ScanJoin.new(p_expr,it.schema)
			resexpr = sexpr.evaluate(TupleSet.new("p",it))
			if resexpr.tups == []
			  require 'ruby-debug'; debugger
			end
			p_var = predoftable(@pexpr)
			spexpr = ScanJoin.new(p_var,resexpr.tups[0].schema)
			respexpr = spexpr.evaluate(resexpr)

			args = Array.new

			aggFunc = ""
			#respexpr.order_by("p_pos") do |var|
			#puts @pexpr
			respexpr.each do |var|
				if (!aggFunc.eql?("")) then
					if (!var.value("type").eql?("var")) then
						raise
					end
					# fix that string stuff!
					aggObj = Aggregate.new(var.value("p_txt"),aggFunc,AggregateFunction.type(aggFunc,String))
#					thisvar = AggregateFunction.function(aggObj)
          				thisvar = aggObj
					thisvar.position = var.value("expr_pos")
					aggFunc = ""
				else 
					case var.value("type")
						when "var"
							thisvar = Variable.new(var.value("p_txt"),String)
							thisvar.position = var.value("expr_pos")
						when "const"
							thisvar = Value.new(var.value("p_txt"))
						when "agg_func"
							aggFunc = var.value("p_txt")	
							next
		
					else
						raise("unhandled type "+var.value("type"))
					end
				end
				args << thisvar
			end
		return args
	end

	def pred_block
		ret  = lambda do |pred,args|
			thispred = Predicate.new(false,TableName.new(nil,pred.value("pred_txt")),Table::Event::NONE,args)
			thispred.set(@progname,rulename,pred.value("pred_pos"))
		end
		return ret
	end


	def plan_whatever(resterm,rulename,entity,sorter,block)
		# resterm is a joinable resultset of terms for the current rule.

		p_pred = predoftable(entity)
		spred = ScanJoin.new(p_pred,resterm.tups[0].schema)
		respred = spred.evaluate(resterm)
	
		predicates = Array.new
		respred.order_by(sorter) do |pred|
			# skip the head, for now
		
			# a row for each predicate.  let's grab the vars
			args = get_vars(pred)
			
	
			#thispred = Predicate.new(false,TableName.new(nil,pred.value("pred_txt")),Table::Event::NONE,args)
			# 2 things about the below.  1.) get rulenames working!  2.) I think the p2 system uses positions starting at 1.
			#thispred.set(@progname,rulename,pred.value("pred_pos"))
			thispred = block(pred,args)
			predicates << thispred
		end
		return predicates
	end


	def plan_preds(resterm,rulename)
		# resterm is a joinable resultset of terms for the current rule.

		p_pred = predoftable(@preds)
		spred = ScanJoin.new(p_pred,resterm.tups[0].schema)
		respred = spred.evaluate(resterm)
	
		predicates = Array.new
		#respred.order_by("term_pos") do |pred|
		respred.each do |pred|
			# skip the head, for now
		
			# a row for each predicate.  let's grab the vars
			args = get_vars(pred)

			(scope,tname) = get_scope(pred.value("pred_txt"))
			
			thispred = Predicate.new(false,TableName.new(scope,tname),Table::Event::NONE,args)
			# 2 things about the below.  1.) get rulenames working!  2.) I think the p2 system uses positions starting at 1.
			thispred.set(@progname,rulename,pred.value("pred_pos"))
			predicates << thispred
		end
		return predicates
	end

	def plan_assignments(resterm,rulename)
		# resterm is a joinable resultset of terms for the current rule.

	
		p_assign = predoftable(@assigns)
		sassign = ScanJoin.new(p_assign,resterm.tups[0].schema)
		resassign = sassign.evaluate(resterm)
	
		assignments = Array.new

		resassign.order_by("assign_pos") do |ass|
			lhs_txt = ass.value("lhs")
			eval_expr = ass.value("assign_txt")

	
			args = get_vars(ass)
			
			lhs = Variable.new(lhs_txt,String)
			lhs.position = 0
			expr = ArbitraryExpression.new(eval_expr,args)
			thisassign = Assignment.new(lhs,expr)
			thisassign.set(@progname,rulename,ass.value("assign_pos"))
			assignments << thisassign
		end
		return assignments 
	end

	def plan_selections(resterm,rulename)
		# resterm is a joinable resultset of terms for the current rule.


		p_select = predoftable(@selects)
		sselect = ScanJoin.new(p_select,resterm.tups[0].schema)
		resselect = sselect.evaluate(resterm)
	
		selections = Array.new

		resselect.order_by("select_pos") do |sel|
			eval_expr = sel.value("select_txt")

			args = get_vars(sel)
			
			expr = ArbitraryExpression.new(eval_expr,args)
			thisselect = SelectionTerm.new(expr)
			thisselect.set(@progname,rulename,sel.value("select_pos"))
			selections << thisselect
		end
		return selections 
	end

end


