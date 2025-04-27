all: build

.PHONY: build
build:
	@echo "Building..."
	@ant jar

.PHONY: release
release:
	@echo "Building release..."
	@ant artifacts -Drelease=true -Dversion=$(VERSION)
	@./scripts/build_rpm.sh --version $(VERSION)
	@./scripts/build_deb.sh --version $(VERSION)

.PHONY: docker-build
docker-build:
	@echo "Building docker image..."
	@docker build \
		-t scylla/cassandra-stress:latest \
		--target production \
		--build-arg CASSANDRA_STRESS_VERSION=3 \
		--compress .

.PHONY: docker-run
docker-run:
	@echo "Running docker image..."
	@docker run \
		-it \
		--name c-s \
		--rm scylla/cassandra-stress:latest \
		cassandra-stress $(ARGS)

.PHONY: setup
setup:
	@echo "Setting up environment..."
	@ant generate-idea-files
	@ant build

.PHONY: clean
clean:
	@echo "Cleaning..."
	@ant realclean
