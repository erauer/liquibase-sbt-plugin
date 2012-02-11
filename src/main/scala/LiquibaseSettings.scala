package com.github.sdb.sbt.liquibase

	
class LiquibaseSettings(val dbMap : Map[String,LiquibaseConfiguration], val groupNameMap : Map[String,Set[String]]) {
	
	lazy val dbNames = dbMap.keySet
	lazy val groupNames = groupNameMap.keySet
	
	def getByDbName(key : String) : Option[LiquibaseConfiguration] ={
		dbMap.get(key)
	}
	
	def getByGroupName(key : String) : Set[LiquibaseConfiguration] = {
		groupNameMap.get(key) match {
			case Some(dbnames) => {
				dbnames map { db =>
					dbMap.get(db)
				}
			}.flatten
			case None => Set.empty[LiquibaseConfiguration]
		}
	}
	
	def get(key:String) : Set[LiquibaseConfiguration] = {
		getByGroupName(key) ++ getByDbName(key).toSet 
	} 
	
}

object LiquibaseSettings {
	
	
	def apply(configs : Seq[(String,LiquibaseConfiguration,String)]) = {
		new LiquibaseSettings(
			configs groupBy (_._1) mapValues (_ map {_._2} head),
			configs groupBy (_._3) mapValues (_ map {_._1} toSet)
		)
	}
	
}