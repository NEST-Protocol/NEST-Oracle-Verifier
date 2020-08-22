package com.nest.ib.contract;

import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

/**
 * <p>Auto generated code.
 * <p><strong>Do not modify!</strong>
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the 
 * <a href="https://github.com/web3j/web3j/tree/master/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 3.3.1.
 */
public class MappingContract extends Contract {
    private static final String BINARY = "608060405234801561001057600080fd5b5060018060003373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200190815260200160002060006101000a81548160ff0219169083151502179055506105df806100776000396000f3fe608060405234801561001057600080fd5b50600436106100575760003560e01c806353b1e0971461005c5780638fe77e8614610137578063a3bf06f114610232578063b6518bdb1461028e578063c7d55057146102d2575b600080fd5b6101356004803603604081101561007257600080fd5b810190808035906020019064010000000081111561008f57600080fd5b8201836020820111156100a157600080fd5b803590602001918460018302840111640100000000831117156100c357600080fd5b91908080601f016020809104026020016040519081016040528093929190818152602001838380828437600081840152601f19601f820116905080830192505050505050509192919290803573ffffffffffffffffffffffffffffffffffffffff169060200190929190505050610316565b005b6101f06004803603602081101561014d57600080fd5b810190808035906020019064010000000081111561016a57600080fd5b82018360208201111561017c57600080fd5b8035906020019184600183028401116401000000008311171561019e57600080fd5b91908080601f016020809104026020016040519081016040528093929190818152602001838380828437600081840152601f19601f8201169050808301925050505050505091929192905050506103db565b604051808273ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200191505060405180910390f35b6102746004803603602081101561024857600080fd5b81019080803573ffffffffffffffffffffffffffffffffffffffff16906020019092919050505061046d565b604051808215151515815260200191505060405180910390f35b6102d0600480360360208110156102a457600080fd5b81019080803573ffffffffffffffffffffffffffffffffffffffff1690602001909291905050506104c3565b005b610314600480360360208110156102e857600080fd5b81019080803573ffffffffffffffffffffffffffffffffffffffff169060200190929190505050610536565b005b600115156103233361046d565b15151461032f57600080fd5b806000836040518082805190602001908083835b602083106103665780518252602082019150602081019050602083039250610343565b6001836020036101000a038019825116818451168082178552505050505050905001915050908152602001604051809103902060006101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff1602179055505050565b600080826040518082805190602001908083835b6020831061041257805182526020820191506020810190506020830392506103ef565b6001836020036101000a038019825116818451168082178552505050505050905001915050908152602001604051809103902060009054906101000a900473ffffffffffffffffffffffffffffffffffffffff169050919050565b6000600160008373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200190815260200160002060009054906101000a900460ff169050919050565b600115156104d03361046d565b1515146104dc57600080fd5b60018060008373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200190815260200160002060006101000a81548160ff02191690831515021790555050565b600115156105433361046d565b15151461054f57600080fd5b6000600160008373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200190815260200160002060006101000a81548160ff0219169083151502179055505056fea265627a7a723158202619ee23ce37cfc39537a810682693117b59f9df4e31e650cdcd0111b15046b364736f6c63430005110032";

    protected MappingContract(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected MappingContract(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static RemoteCall<MappingContract> deploy(Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(MappingContract.class, web3j, credentials, gasPrice, gasLimit, BINARY, "");
    }

    public static RemoteCall<MappingContract> deploy(Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(MappingContract.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, "");
    }

    public RemoteCall<TransactionReceipt> addContractAddress(String name, String contractAddress) {
        final Function function = new Function(
                "addContractAddress", 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(name),
                new Address(contractAddress)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> addSuperMan(String superMan) {
        final Function function = new Function(
                "addSuperMan", 
                Arrays.<Type>asList(new Address(superMan)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<String> checkAddress(String name) {
        final Function function = new Function("checkAddress",
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(name)),
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteCall<Boolean> checkOwners(String man) {
        final Function function = new Function("checkOwners",
                Arrays.<Type>asList(new Address(man)),
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    public RemoteCall<TransactionReceipt> deleteSuperMan(String superMan) {
        final Function function = new Function(
                "deleteSuperMan", 
                Arrays.<Type>asList(new Address(superMan)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public static MappingContract load(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new MappingContract(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    public static MappingContract load(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new MappingContract(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }
}
