#!/usr/bin/env python3

#
# Copyright (C) 2018 ScyllaDB
#

#
# This file is part of Scylla.
#
# Scylla is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# Scylla is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with Scylla.  If not, see <http://www.gnu.org/licenses/>.
#

from argparse import ArgumentParser
from tarfile import TarFile, open as taropen

RELOC_PREFIX='cassandra-stress'
def reloc_add(self, name, arcname=None, recursive=True, *, filter=None):
    if arcname:
        return self.add(name, arcname=f"{RELOC_PREFIX}/{arcname}")
    else:
        return self.add(name, arcname=f"{RELOC_PREFIX}/{name}")

TarFile.reloc_add = reloc_add

ap = ArgumentParser(description='Create a relocatable scylla package.')
ap.add_argument('--version', required=True,
                help='Tools version')
ap.add_argument('dest',
                help='Destination file (tar format)')

args = ap.parse_args()

version = args.version
output = args.dest

ar = taropen(output, mode='w|gz')
# relocatable package format version = 2
with open('build/.relocatable_package_version', 'w') as f:
    f.write('2\n')
ar.add('build/.relocatable_package_version', arcname='.relocatable_package_version')

ar.reloc_add('dist')
ar.reloc_add('conf')
ar.reloc_add('bin')
ar.reloc_add('tools')
ar.reloc_add('lib')
ar.reloc_add('doc')
ar.reloc_add('install.sh')
ar.reloc_add(f'build/apache-cassandra-{version}.jar', arcname=f'lib/apache-cassandra-{version}.jar')
ar.reloc_add(f'build/apache-cassandra-thrift-{version}.jar', arcname=f'lib/apache-cassandra-thrift-{version}.jar')
ar.reloc_add(f'build/scylla-tools-{version}.jar', arcname=f'lib/scylla-tools-{version}.jar')
ar.reloc_add('build/tools/lib/stress.jar', arcname='lib/stress.jar')
ar.reloc_add('README.md')
ar.reloc_add('CHANGES.txt')
ar.reloc_add('NEWS.txt')
ar.reloc_add('CASSANDRA-14092.txt')
ar.reloc_add('build/debian/debian', arcname='debian')
