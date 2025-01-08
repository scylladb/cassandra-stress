Name:           cassandra-stress
Version:        %{version}
Release:        1
Summary:        Scylla Cassandra Stress
Group:          Applications/Databases

License:        Apache
URL:            http://www.scylladb.com/
Source0:        cassandra-stress.tar.gz
BuildArch:      noarch
Requires:       jre-11-headless
AutoReqProv:    no

%description
Cassandra-stress is a Java-based command-line stress testing tool
for Apache Cassandra and Scylla.

%prep
%setup -q -n cassandra-stress

%build

%install

install -d -m 0755 %{buildroot}/etc/cassandra-stress
install -d -m 0755 %{buildroot}/usr/share/cassandra-stress/lib
install -d -m 0755 %{buildroot}/usr/share/cassandra-stress/bin

install -m 0644 conf/* %{buildroot}/etc/cassandra-stress/
install -m 0644 lib/*.jar %{buildroot}/usr/share/cassandra-stress/lib/
install -m 0755 bin/cassandra-stress %{buildroot}/usr/share/cassandra-stress/bin/cassandra-stress


%files

%config(noreplace) /etc/cassandra-stress/*
/usr/share/cassandra-stress/
/usr/share/cassandra-stress/bin/cassandra-stress


%changelog
* Fri Aug  7 2015 Takuya ASADA Takuya ASADA <syuu@cloudius-systems.com>
- inital version of scylla-tools.spec
