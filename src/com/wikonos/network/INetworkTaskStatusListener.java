package com.wikonos.network;

public interface INetworkTaskStatusListener {
	void nTaskSucces(NetworkResult result);
	void nTaskErr(NetworkResult result);
}
