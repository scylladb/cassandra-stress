Name:           cassandra-stress
Version:        %{version}
Release:        %{release}%{?dist}
Summary:        Scylla Cassandra Stress
Group:          Applications/Databases

License:        Apache
URL:            http://www.scylladb.com/
Source0:        %{reloc_pkg}
BuildArch:      noarch
Requires:
AutoReqProv:    no

%description

%package core
License:        Apache
URL:            http://www.scylladb.com/
BuildArch:      noarch
Summary:        Core files for Scylla tools
Version:        %{version}
Release:        %{release}%{?dist}
Requires:       jre-11-headless

%global __brp_python_bytecompile %{nil}
%global __brp_mangle_shebangs %{nil}

%prep
%setup -q -n scylla-tools


%build

%install
rm -rf $RPM_BUILD_ROOT
./install.sh --root "$RPM_BUILD_ROOT"

%files
/opt/scylladb/share/cassandra-stress/bin/cassandra-stress
/opt/scylladb/share/cassandra-stress/bin/cassandra-stressd
%{_bindir}/cassandra-stress
%{_bindir}/cassandra-stressd

%files core
%{_sysconfdir}/scylla/cassandra-stress/cassandra-env.sh
%{_sysconfdir}/scylla/cassandra-stress/logback.xml
%{_sysconfdir}/scylla/cassandra-stress/logback-tools.xml
%{_sysconfdir}/scylla/cassandra-stress/jvm*.options
/opt/scylladb/share/cassandra-stress/bin/cassandra.in.sh
/opt/scylladb/share/cassandra-stress/lib/*.jar

%changelog
* Fri Aug  7 2015 Takuya ASADA Takuya ASADA <syuu@cloudius-systems.com>
- inital version of scylla-tools.spec
