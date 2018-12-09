import React from 'react';
import createBrowserHistory from 'history/createBrowserHistory';
import {Route, Router, Switch} from 'react-router';
import {connect} from 'react-redux';
import {Badge} from 'reactstrap';

import Menu from '../components/general/Menu';
import Start from '../components/views/Start';
import ConfigurationPage from '../components/views/config/ConfigurationPage';
import RestServices from '../components/views/develop/RestServices';
import {isDevelopmentMode} from '../utilities/global';
import LogPage from '../components/views/logging/LogPage';
import Footer from '../components/views/footer/Footer';
import {loadVersion} from '../actions';
import {getTranslation} from '../utilities/i18n';
import I18n from "../components/general/translation/I18n";

class WebApp extends React.Component {

    history = createBrowserHistory();

    componentDidMount = () => {
        this.props.loadVersion();
    };

    render() {
        let routes = [
            ['Start', '/', Start],
            ['Log viewer', '/logging', LogPage],
            ['Configuration', '/config', ConfigurationPage]
        ];

        if (isDevelopmentMode()) {
            routes.push(['Rest services', '/restServices', RestServices]);
        }

        return (
            <Router history={this.history}>
                <div>
                    <Menu routes={routes}/>
                    <div className={'container main-view'}>
                        <Switch>
                            {
                                routes.map((route, index) => (
                                    <Route
                                        key={index}
                                        path={route[1]}
                                        component={route[2]}
                                        exact
                                    />
                                ))
                            }
                        </Switch>
                    </div>
                    <Footer versionInfo={this.props.version}/>
                </div>
            </Router>
        );
    }
}

const mapStateToProps = state => ({
    version: state.version
});

const actions = {
    loadVersion
};

export default connect(mapStateToProps, actions)(WebApp);