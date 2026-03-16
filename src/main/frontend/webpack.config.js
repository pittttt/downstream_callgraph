const HtmlWebpackPlugin = require('html-webpack-plugin');
const path = require('path');

module.exports = [
    {
        entry: './src/js/callgraph.js',
        output: {
            filename: 'callgraph.js',
            path: path.resolve(__dirname, '../resources/build')
        },
        module: {
            rules: [
                {
                    test: /\.css$/i,
                    use: ['style-loader', 'css-loader']
                },
                {
                    test: /\.(woff|woff2|eot|ttf|otf)$/i,
                    type: 'asset/inline'
                }
            ]
        },
        plugins: [
            new HtmlWebpackPlugin({
                template: './src/html/callgraph.html',
                inject: false,
                filename: 'callgraph.html'
            })
        ]
    },
    {
        entry: './src/js/saveas.js',
        output: {
            filename: 'saveas.js',
            path: path.resolve(__dirname, '../resources/build')
        },
        module: {
            rules: [
                {
                    test: /\.css$/i,
                    use: ['style-loader', 'css-loader']
                },
                {
                    test: /\.(woff|woff2|eot|ttf|otf)$/i,
                    type: 'asset/inline'
                }
            ]
        },
        plugins: [
            new HtmlWebpackPlugin({
                template: './src/html/saveas.html',
                inject: false,
                filename: 'saveas.html'
            })
        ]
    }
]
