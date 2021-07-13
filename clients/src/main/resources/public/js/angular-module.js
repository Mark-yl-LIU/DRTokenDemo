const app = angular.module('auctionApp', ['ui.bootstrap', 'toastr']);

let httpHeaders = {
        headers : {
            "Content-Type": "application/x-www-form-urlencoded"
        }
    };

app.controller('AppController', function($http, toastr, $uibModal) {
    const demoApp = this;
    const apiBaseURL = "/api/example/";

    demoApp.landingScreen = false;
    demoApp.homeScreen = true;
    demoApp.activeParty = "Investor";
    demoApp.assetMap = {};
    demoApp.balance = 0;
    demoApp.showSpinner = false;
    demoApp.showAuctionSpinner = false;
    demoApp.showAssetSpinner = false;
    demoApp.Title = btoa("\\r\u0014èéÈ²Ë\\ré¨§^ºò1ªä")

    demoApp.skipDataSetup = () => {
        demoApp.landingScreen = false;
        demoApp.homeScreen = true;
        demoApp.fetchAssets();
        demoApp.getallcash();
    }

    demoApp.switchParty = (party) => {
        $http.post(apiBaseURL + 'switch-party/' + party)
        .then((response) => {
           if(response.data && response.data.status){
               demoApp.activeParty = party;
               demoApp.fetchAssets();
               demoApp.getallcash();
               toastr.success('Switched to '+ party);
           }else{
               toastr.error(response.data? response.data.message: "Something went wrong. Please try again later!");
           }
        });
    }

    demoApp.fetchAssets = () => {
        demoApp.showAssetSpinner = true;
        $http.get(apiBaseURL + 'asset/list')
            .then((response) => {
                if(response.data && response.data.status){
                    demoApp.assets = [];
                    demoApp.assetsnum = [];
                    for(let i in response.data.data){
                        verstr = response.data.data[i].state.data;
                        assettype = verstr.amount;
                        assetnum = parseFloat(assettype)

                        if (assettype.indexOf('ShareState') >= 0 && assetnum > 0) {
                            demoApp.assetsnum.push((verstr));

                            $http.get(apiBaseURL + 'asset/list/sharestate')
                                .then((response) => {
                                    for(let j in response.data.data){
                                        demoApp.assets.push(response.data.data[j]);
                                        demoApp.assetMap[response.data.data[j].state.data.linearId.id] = response.data.data[j];
                                    };
                                });

                        }
                        if (assettype.indexOf('DRTokenState') >= 0 && assetnum > 0) {
                            demoApp.assetsnum.push((verstr));
                            $http.get(apiBaseURL + 'asset/list/drtoken')
                                .then((response) => {
                                    for(let j in response.data.data){
                                        demoApp.assets.push(response.data.data[j]);
                                        demoApp.assetMap[response.data.data[j].state.data.linearId.id] = response.data.data[j];
                                    }
                                });
                        }
                    }
                }else{
                    toastr.error(response.data? response.data.message: "Something went wrong. Please try again later!");
                }
                demoApp.showAssetSpinner = false;
            });
    }


    demoApp.getallcash = () =>{
        demoApp.showAssetSpinner = true;
        $http.get(apiBaseURL + 'cashall')
            .then((response) => {
                if (response.data && response.data.status) {
                    demoApp.fiatCashMaps = [];
                    for (let i in response.data.data) {
                        verstr = response.data.data[i];
                        // Filter the Cash one
                        cashtype = verstr.state.data.amount;
                        if (cashtype.indexOf('TokenType') >= 0) {
                            demoApp.fiatCashMaps.push((verstr));
                        }

                    }
                } else {
                    toastr.error(response.data ? response.data.message : "Something went wrong. Please try again later!");
                }
                demoApp.showAssetSpinner = false;
            });
    }

    demoApp.openIssueCashModal = (assetId) => {
        const cashModal = $uibModal.open({
            templateUrl: 'issueCashModal.html',
            controller: 'CashModalCtrl',
            controllerAs: 'cashModalCtrl',
            resolve: {
                demoApp: () => demoApp,
                apiBaseURL: () => apiBaseURL,
                toastr: () => toastr,
            }
        });

        cashModal.result.then(() => {}, () => {});
    };

    demoApp.openIssueStockModal = (assetId) => {
        const stockModal = $uibModal.open({
            templateUrl: 'issueshareModal.html',
            controller: 'ShareModalCtrl',
            controllerAs: 'shareModalCtrl',
            resolve: {
                demoApp: () => demoApp,
                apiBaseURL: () => apiBaseURL,
                toastr: () => toastr,
            }
        });

        stockModal.result.then(() => {}, () => {});
    };

    demoApp.openBuyDRModal = ()=> {
        const drModal = $uibModal.open({
            templateUrl: 'buyDRModal.html',
            controller: 'BuyDRModalCtrl',
            controllerAs: 'buyDRModalCtrl',
            windowClass: 'app-modal-window',
            resolve: {
                demoApp: () => demoApp,
                apiBaseURL: () => apiBaseURL,
                toastr: () => toastr,
            }
        });

        drModal.result.then(() => {}, () => {});
    };

    demoApp.skipDataSetup();

});


app.controller('CreateAssetModalCtrl', function ($http, $uibModalInstance, $uibModal, demoApp, apiBaseURL, toastr) {
    const createAssetModel = this;

    createAssetModel.form = {};

    createAssetModel.create = () => {
        if(createAssetModel.form.imageUrl == undefined || createAssetModel.form.title == undefined ||
        createAssetModel.form.description == undefined || createAssetModel.form.imageUrl == '' ||
        createAssetModel.form.title == '' || createAssetModel.form.description == '' ){
           toastr.error("All fields are mandatory!");
        }else{
           demoApp.showSpinner = true;
           $http.post(apiBaseURL + 'asset/create', createAssetModel.form)
           .then((response) => {
              if(response.data && response.data.status){
                  toastr.success('Asset Create Successfully');
                  demoApp.fetchAssets();
                  $uibModalInstance.dismiss();
              }else{
                  toastr.error(response.data? response.data.message: "Something went wrong. Please try again later!");
              }
              demoApp.showSpinner = false;
           });
        }
    }

    createAssetModel.cancel = () => $uibModalInstance.dismiss();

});

app.controller('CashModalCtrl', function ($http, $uibModalInstance, $uibModal, demoApp, apiBaseURL, toastr) {
    const cashModalModel = this;

    cashModalModel.form = {};
    cashModalModel.form.party = demoApp.activeParty;
    cashModalModel.issueCash = () => {
        if(cashModalModel.form.amount == undefined){
           toastr.error("Please enter amount to be issued");
        }else{
            demoApp.showSpinner = true;
            $http.post(apiBaseURL + 'issueCash', cashModalModel.form)
            .then((response) => {
               if(response.data && response.data.status){
                   toastr.success('Cash Deposited Successfully');
                   $uibModalInstance.dismiss();
               }else{
                   toastr.error(response.data? response.data.message: "Something went wrong. Please try again later!");
               }
               demoApp.showSpinner = false;
            });
        }
    }

    cashModalModel.cancel = () => $uibModalInstance.dismiss();

});

app.controller('ShareModalCtrl', function ($http, $uibModalInstance, $uibModal, demoApp, apiBaseURL, toastr) {
    const shareModalModel = this;

    shareModalModel.form = {};
    shareModalModel.issueShare = () => {
        if(shareModalModel.form.amount == undefined){
            toastr.error("Please enter amount to be issued");
        }else{
            demoApp.showSpinner = true;
            $http.post(apiBaseURL + 'issueShare', shareModalModel.form)
                .then((response) => {
                    if(response.data && response.data.status){
                        toastr.success('Share Issued Successfully');
                        $uibModalInstance.dismiss();
                    }else{
                        toastr.error(response.data? response.data.message: "Something went wrong. Please try again later!");
                    }
                    demoApp.showSpinner = false;
                });
        }
    }

    shareModalModel.cancel = () => $uibModalInstance.dismiss();

});

app.controller('BuyDRModalCtrl', function ($http, $uibModalInstance, $uibModal, demoApp, apiBaseURL, toastr) {
    const buyDRModalModel = this;

    buyDRModalModel.form = {};
    buyDRModalModel.buyDR = () => {
        if(buyDRModalModel.form.quantity == undefined){
            toastr.error("Please enter amount to be issued");
        }else{
            demoApp.showSpinner = true;
            $http.post(apiBaseURL + 'buyDR', buyDRModalModel.form)
                .then((response) => {
                    if(response.data && response.data.status){
                        toastr.success('DR Purchase Successfully');
                        $uibModalInstance.dismiss();
                    }else{
                        toastr.error(response.data? response.data.message: "Something went wrong. Please try again later!");
                    }
                    demoApp.showSpinner = false;
                });
        }
    }

    buyDRModalModel.cancel = () => $uibModalInstance.dismiss();

});
