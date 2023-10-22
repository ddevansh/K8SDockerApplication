package com.example.DockerK8S;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.amazonaws.DefaultRequest;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.SdkClock;
import com.amazonaws.auth.Signer;
import com.amazonaws.auth.SignerFactory;
import com.amazonaws.auth.SignerParams;
import com.amazonaws.auth.presign.PresignerFacade;
import com.amazonaws.auth.presign.PresignerParams;
import com.amazonaws.http.HttpMethodName;
import com.amazonaws.internal.auth.DefaultSignerProvider;
import com.amazonaws.internal.auth.SignerProvider;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;

import io.fabric8.kubernetes.api.model.ConfigBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

@RestController
public class SpringBootDockerK8S {

	static Logger logger = LoggerFactory.getLogger(SpringBootDockerK8S.class);

	@PostMapping("/create-start-docker-nginx")
	public static void createAndStartDockerNginx() throws InterruptedException {

		DockerClientConfig custom = DefaultDockerClientConfig.createDefaultConfigBuilder()
				.withDockerHost("tcp://hub.docker.com").withRegistryUsername("ddevansh2")
				.withRegistryPassword("Check@12345").withRegistryEmail("ddevansh.sharma@yahoo.co.in").build();

		DockerClient dockerClient = DockerClientBuilder.getInstance(custom).build();

		ExposedPort tcpPort = ExposedPort.tcp(4444);
		CreateContainerResponse ngnixContainer = dockerClient.createContainerCmd("ddevansh2/ngnix").withName("testngnix")
				.withImage("ngnix:1.25.2").withExposedPorts(tcpPort).withAttachStderr(false).withAttachStdin(false)
				.withAttachStdout(false).exec();

		// Actually run the container
		dockerClient.startContainerCmd(ngnixContainer.getId()).exec();
		
		InspectImageResponse resp = dockerClient.inspectImageCmd(ngnixContainer.getId()).exec();
		resp.getContainerConfig().getEntrypoint();
	}

	@PostMapping("/stop-docker-nginx/{containerId}")
	public static void stopDockerNginx(@PathVariable("containerId") String containerId) throws InterruptedException {

		DockerClientConfig custom = DefaultDockerClientConfig.createDefaultConfigBuilder()
				.withDockerHost("tcp://hub.docker.com").withRegistryUsername("ddevansh2")
				.withRegistryPassword("Check@12345").withRegistryEmail("ddevansh.sharma@yahoo.co.in").build();

		DockerClient dockerClient = DockerClientBuilder.getInstance(custom).build();

		// Stop run the container with the container id
		dockerClient.stopContainerCmd(containerId).withTimeout(2).exec();
	}

	@GetMapping("/get-page-docker")
	public static String getDockerImages() throws InterruptedException {

		String dockerImageId = null;

		DockerClientConfig custom = DefaultDockerClientConfig.createDefaultConfigBuilder()
				.withDockerHost("tcp://hub.docker.com:2376").withRegistryUsername("ddevansh2")
				.withRegistryPassword("Check@12345").withRegistryEmail("ddevansh.sharma@yahoo.co.in").build();

		DockerClient dockerClient = DockerClientBuilder.getInstance(custom).build();

		ExposedPort tcpPort = ExposedPort.tcp(4444);
		CreateContainerResponse ngnixContainer = dockerClient.createContainerCmd("ddevansh2/ngnix").withName("testngnix")
				.withImage("ngnix:1.25.2").withExposedPorts(tcpPort).withAttachStderr(false).withAttachStdin(false)
				.withAttachStdout(false).exec();

		// Actually run the container
		dockerClient.startContainerCmd(ngnixContainer.getId()).exec();
		
		InspectImageResponse resp = dockerClient.inspectImageCmd(ngnixContainer.getId()).exec();
		
		//Approach 1
		String[] entrypoint = resp.getContainerConfig().getEntrypoint();
		
		//Approach 2
		ExposedPort[] exposedPorts = resp.getContainerConfig().getExposedPorts();
		
		//Approach 3
		String domainName = resp.getContainerConfig().getDomainName();
		
		

		return dockerImageId;
	}

	@PostMapping("/create-start-docker-k8s")
	public static void createAndStartK8SNginx() throws InterruptedException {

		String clusterEndPoint = "https://ED94B864F09ABD40E999ED15E0741868.gr7.ap-southeast-2.eks.amazonaws.com";

		String accessKey = "AKIAWZMC54GDBYHCHLSH";
		String secretKey = "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";

		String region = "ap-southeast-2";

		String accessToken = generateEksToken(clusterEndPoint, region, accessKey, secretKey);

		// Create a pod (using Deployment) with a container created from ngnix image
		KubernetesClient client = new DefaultKubernetesClient(new ConfigBuilder().withContexts(clusterEndPoint)
				.withOauthToken(accessToken).withTrustCerts(true).build());
		Deployment nginxDeployment = new DeploymentBuilder().withNewMetadata().withName("nginx-deployment")
				.addToLabels("app", "nginx").endMetadata().withNewSpec().withReplicas(1).withNewSelector()
				.addToMatchLabels("app", "nginx").endSelector().withNewTemplate().withNewMetadata()
				.addToLabels("app", "nginx").endMetadata().withNewSpec().addNewContainer().withName("nginx")
				.withImage("nginx:1.25.2").addNewPort().withContainerPort(80).endPort().endContainer().endSpec()
				.endTemplate().endSpec().build();

		client.apps().deployments().inNamespace("default").createOrReplace(nginxDeployment);

		// Create a pod using PodBuilder
		Pod aPod = new PodBuilder().withNewMetadata().withName("demo-pod1").endMetadata().withNewSpec()
				.addNewContainer().withName("nginx").withImage("nginx:1.25.2").addNewPort().withContainerPort(80)
				.endPort().endContainer().endSpec().build();
		Pod createdPod = client.pods().inNamespace("default").create(aPod);

		/*
		 * Below is the code to read the test-svc.yml file (service is used to access
		 * the pods) Service service = client.services()
		 * .load(LoadServiceYaml.class.getResourceAsStream("/test-svc.yml")) .get();
		 */
	}

	@PostMapping("/stop-docker-k8s")
	public static void stopDockerK8S(String containerId) throws InterruptedException {

		String clusterEndPoint = "https://ED94B864F09ABD40E999ED15E0741868.gr7.ap-southeast-2.eks.amazonaws.com";

		String accessKey = "test1";
		String secretKey = "test5";

		String region = "ap-southeast-2";

		String accessToken = generateEksToken(clusterEndPoint, region, accessKey, secretKey);

		// Create a pod (using Deployment) with a container created from ngnix image
		KubernetesClient client = new DefaultKubernetesClient(new ConfigBuilder().withContexts(clusterEndPoint)
				.withOauthToken(accessToken).withTrustCerts(true).build());
		Deployment deployment = new DeploymentBuilder().withNewMetadata().withName("nginx-deployment")
				.addToLabels("app", "nginx").endMetadata().withNewSpec().withReplicas(1).withNewSelector()
				.addToMatchLabels("app", "nginx").endSelector().withNewTemplate().withNewMetadata()
				.addToLabels("app", "nginx").endMetadata().withNewSpec().addNewContainer().withName("nginx")
				.withImage("nginx:1.25.2").addNewPort().withContainerPort(80).endPort().endContainer().endSpec()
				.endTemplate().endSpec().build();

		client.apps().deployments().inNamespace("default").delete(deployment);
	}

	@GetMapping("/get-page-k8s")
	public static PodList getPageK8S() throws InterruptedException {

		String clusterEndPoint = "https://ED94B864F09ABD40E999ED15E0741868.gr7.ap-southeast-2.eks.amazonaws.com";

		String accessKey = "AKIAWZMC54GDBYHCHLSH";
		String secretKey = "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";

		String region = "ap-southeast-2";

		String accessToken = generateEksToken(clusterEndPoint, region, accessKey, secretKey);

		// Create a pod (using Deployment) with a container created from ngnix image
		KubernetesClient client = new DefaultKubernetesClient(new ConfigBuilder().withContexts(clusterEndPoint)
				.withOauthToken(accessToken).withTrustCerts(true).build());

		PodList podList = client.pods().inNamespace("default").list();

		return podList;
	}

	public static String generateEksToken(String clusterName, String region, String accessKey, String secretKey)
			throws URISyntaxException {

		DefaultRequest defaultRequest = new DefaultRequest<>(

				new GetCallerIdentityRequest(), "sts");

		URI uri = new URI("https", "sts.ap-southeast-2.amazonaws.com", null, null);

		defaultRequest.setResourcePath("/");

		defaultRequest.setEndpoint(uri);

		defaultRequest.setHttpMethod(HttpMethodName.GET);

		defaultRequest.addParameter("Action", "GetCallerIdentity");

		defaultRequest.addParameter("Version", "2011-06-15");

		defaultRequest.addHeader("x-k8s-aws-id", clusterName);

		BasicAWSCredentials basicCredentials = new BasicAWSCredentials(accessKey, secretKey);

		AWSStaticCredentialsProvider credentials = new AWSStaticCredentialsProvider(basicCredentials);

		Signer signer = SignerFactory.createSigner(SignerFactory.VERSION_FOUR_SIGNER,

				new SignerParams("sts", region));

		AWSSecurityTokenServiceClient stsClient = (AWSSecurityTokenServiceClient) AWSSecurityTokenServiceClientBuilder

				.standard().withRegion(region).withCredentials(credentials).build();

		SignerProvider signerProvider = new DefaultSignerProvider(stsClient, signer);

		PresignerParams presignerParams = new PresignerParams(uri, credentials, signerProvider,

				SdkClock.STANDARD);

		PresignerFacade presignerFacade = new PresignerFacade(presignerParams);

		URL url = presignerFacade.presign(defaultRequest, new Date(System.currentTimeMillis() + 60000));

		String encodedUrl = Base64.getUrlEncoder().withoutPadding().encodeToString(url.toString().getBytes());

		return "k8s-aws-v1." + encodedUrl;

	}
}
