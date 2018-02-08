-- alter table accounts_tenants drop constraint FKFDA649B05A9CEEB5;
DROP TABLE IF EXISTS accounts_common CASCADE;
DROP TABLE IF EXISTS accounts_tenants CASCADE;
DROP TABLE IF EXISTS tenants CASCADE;
DROP SEQUENCE IF EXISTS hibernate_sequence;
create table accounts_common (csid varchar(128) not null, created_at timestamp not null, email varchar(255) not null, mobile varchar(255), person_ref_name varchar(255), phone varchar(255), screen_name varchar(128) not null, 
	status varchar(15) not null, updated_at timestamp, userid varchar(128) not null, 
	metadata_protection varchar(255), roles_protection varchar(255), 
	primary key (csid), unique (userid));

	create table accounts_tenants (HJID int8 not null, tenant_id varchar(128) not null, TENANTS_ACCOUNTS_COMMON_CSID varchar(128), primary key (HJID));
create table tenants (id varchar(128) not null, created_at timestamp not null, name varchar(255) not null, config_md5hash varchar(255), authorities_initialized boolean not null, disabled boolean not null, updated_at timestamp, primary key (id));
alter table accounts_tenants add constraint FKFDA649B05A9CEEB5 foreign key (TENANTS_ACCOUNTS_COMMON_CSID) references accounts_common;
create sequence hibernate_sequence;
