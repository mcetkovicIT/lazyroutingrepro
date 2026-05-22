create table if not exists routing_probe (
  id bigserial primary key,
  marker varchar(32) not null
);

create table if not exists write_audit (
  id bigserial primary key,
  note varchar(128) not null,
  created_at timestamptz not null default now()
);

delete from routing_probe;
insert into routing_probe(marker) values ('SLAVE');