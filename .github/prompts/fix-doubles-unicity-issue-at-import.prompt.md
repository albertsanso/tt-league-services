# Summary

At import time, we have a problem with the unicity of the doubles. 

# Description

In some cases the doubles are not unique and can cause issues in the system.
We need to fix this issue by ensuring that the doubles are unique at import time.

An example of the error and exception that is thrown when this issue occurs is the following:
```text
org.springframework.dao.DataIntegrityViolationException: could not execute batch [Batch entry 1 insert into doubles_pair (game_id,player_id,side,id) values (('1b9d380f-c239-4905-9efc-6299f2fbea9d'::uuid),('d20aa6ab-4471-4e6b-a08a-006c4ee3162c'::uuid),('HOME'),('d36f6d47-4434-43f8-9fd8-4f881fb2c7aa'::uuid)) was aborted: ERROR: llave duplicada viola restricci�n de unicidad �uk_doubles_pair_game_side_player�
  Detail: Ya existe la llave (game_id, side, player_id)=(1b9d380f-c239-4905-9efc-6299f2fbea9d, HOME, d20aa6ab-4471-4e6b-a08a-006c4ee3162c).  Call getNextException to see other errors in the batch.] [insert into doubles_pair (game_id,player_id,side,id) values (?,?,?,?)]; SQL [insert into doubles_pair (game_id,player_id,side,id) values (?,?,?,?)]; constraint [null]
	at org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:294) ~[spring-orm-6.2.14.jar:6.2.14]
	at org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:256) ~[spring-orm-6.2.14.jar:6.2.14]
	at org.springframework.orm.jpa.vendor.HibernateJpaDialect.translateExceptionIfPossible(HibernateJpaDialect.java:241) ~[spring-orm-6.2.14.jar:6.2.14]
	at org.springframework.orm.jpa.JpaTransactionManager.doCommit(JpaTransactionManager.java:567) ~[spring-orm-6.2.14.jar:6.2.14]
	at org.springframework.transaction.support.AbstractPlatformTransactionManager.processCommit(AbstractPlatformTransactionManager.java:795) ~[spring-tx-6.2.14.jar:6.2.14]
	at org.springframework.transaction.support.AbstractPlatformTransactionManager.commit(AbstractPlatformTransactionManager.java:758) ~[spring-tx-6.2.14.jar:6.2.14]
	at org.springframework.transaction.interceptor.TransactionAspectSupport.commitTransactionAfterReturning(TransactionAspectSupport.java:698) ~[spring-tx-6.2.14.jar:6.2.14]
	at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:416) ~[spring-tx-6.2.14.jar:6.2.14]
```

# Affected actas JSON files

This is the list of JSON actas files that are affected by this issue:
```text
C:\git\rfetm-extract-2\resources\actas-json\2025-2026\primera-nacional\2\masculino\acta_28086.json
C:\git\rfetm-extract-2\resources\actas-json\2025-2026\primera-nacional\6\femenino\acta_31727.json
C:\git\rfetm-extract-2\resources\actas-json\2025-2026\primera-nacional\6\masculino\acta_28093.json
C:\git\rfetm-extract-2\resources\actas-json\2025-2026\primera-nacional\6\masculino\acta_28821.json
C:\git\rfetm-extract-2\resources\actas-json\2025-2026\segona-nacional\6\masculino\acta_29572.json
C:\git\rfetm-extract-2\resources\actas-json\2025-2026\segona-nacional\9\masculino\acta_29826.json
```

The other JSON actas files are not affected by this issue.

