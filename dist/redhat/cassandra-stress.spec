Name:           cassandra-stress
Version:        %{version}
Release:        1
Summary:        Scylla Cassandra Stress
Group:          Applications/Databases

License:        Apache
URL:            http://www.scylladb.com/
Source0:        cassandra-stress.tar.gz
BuildArch:      noarch
Requires:       (jre-11-headless or temurin-11-jre) snappy
AutoReqProv:    no

%description
Cassandra-stress is a Java-based command-line stress testing tool
for Apache Cassandra and Scylla.

%prep
%setup -q -n cassandra-stress

%build

%install

rm -rf %{buildroot}

install -d -m 0755 %{buildroot}%{_sysconfdir}/cassandra-stress
install -d -m 0755 %{buildroot}%{_datadir}/cassandra-stress/lib
install -d -m 0755 %{buildroot}%{_datadir}/cassandra-stress/bin
install -d -m 0755 %{buildroot}%{_bindir}

install -m 0644 conf/* %{buildroot}%{_sysconfdir}/cassandra-stress
install -m 0644 lib/*.jar %{buildroot}%{_datadir}/cassandra-stress/lib
install -m 0755 bin/cassandra-stress %{buildroot}%{_datadir}/cassandra-stress/bin
ln -s %{_datadir}/cassandra-stress/bin/cassandra-stress %{buildroot}%{_bindir}/cassandra-stress

%files

%config(noreplace) %{_sysconfdir}/cassandra-stress/*
%{_datadir}/cassandra-stress/lib/*.jar
%{_datadir}/cassandra-stress/bin/cassandra-stress
%{_bindir}/cassandra-stress

%changelog
* Fri Aug  7 2015 Takuya ASADA Takuya ASADA <syuu@cloudius-systems.com>
- inital version of scylla-tools.spec
